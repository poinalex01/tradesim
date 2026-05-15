package com.tradesim.service;

import com.tradesim.dto.GameUpdateMessage;
import com.tradesim.entity.*;
import com.tradesim.repository.LobbyRepository;
import com.tradesim.repository.MarketCandleRepository;
import com.tradesim.repository.PortfolioRepository;
import com.tradesim.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameEngineService {

    private final LobbyRepository lobbyRepository;
    private final MarketCandleRepository marketCandleRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SeasonService seasonService;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        List<Lobby> runningLobbies = lobbyRepository.findByStatus(LobbyStatus.RUNNING);

        for (Lobby lobby : runningLobbies) {
            if (lobby.getLastTickTime() != null) {
                long secondsSinceLastTick = java.time.Duration.between(
                        lobby.getLastTickTime(), LocalDateTime.now()).getSeconds();
                if (secondsSinceLastTick < lobby.getTickIntervalSeconds()) continue;
            }

            List<MarketCandle> candles = marketCandleRepository
                    .findByDatasetAndAssetOrderByTimestampAsc(lobby.getDataset(), getAssetForDataset(lobby.getDataset()));

            if (candles.isEmpty()) continue;

            int nextTick = lobby.getCurrentTickIndex() + 1;

            if (nextTick >= candles.size()) {
                lobby.setStatus(LobbyStatus.FINISHED);
                lobby.setEndedAt(LocalDateTime.now());
                lobbyRepository.save(lobby);

                List<Portfolio> portfolios = portfolioRepository.findByLobby(lobby);
                portfolios.sort((a, b) -> Double.compare(b.getCashBalance(), a.getCashBalance()));

                // close all positions
                for (Portfolio p : portfolios) {
                    List<Position> openPositions = positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN);
                    for (Position pos : openPositions) {
                        double currentPrice = marketDataService.getCurrentPrice(
                                pos.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
                        double returnValue = calculateReturnValue(pos, currentPrice);
                        p.setCashBalance(p.getCashBalance() + returnValue);
                        pos.setStatus(PositionStatus.CLOSED);
                        pos.setClosedAt(LocalDateTime.now());
                        positionRepository.save(pos);
                    }
                    portfolioRepository.save(p);
                }

                for (int i = 0; i < portfolios.size(); i++) {
                    Portfolio p = portfolios.get(i);
                    double profit = p.getCashBalance() - p.getStartBalance();
                    boolean won = i == 0;
                    seasonService.updateSeasonStats(p.getUser(), profit, won, lobby.getGameMode());
                }

                messagingTemplate.convertAndSend(
                        "/topic/lobby/" + lobby.getId(),
                        GameUpdateMessage.builder()
                                .lobbyId(lobby.getId())
                                .tickIndex(lobby.getCurrentTickIndex())
                                .asset(getAssetForDataset(lobby.getDataset()))
                                .currentPrice(candles.get(lobby.getCurrentTickIndex()).getClose())
                                .open(candles.get(lobby.getCurrentTickIndex()).getOpen())
                                .high(candles.get(lobby.getCurrentTickIndex()).getHigh())
                                .low(candles.get(lobby.getCurrentTickIndex()).getLow())
                                .close(candles.get(lobby.getCurrentTickIndex()).getClose())
                                .timestamp(candles.get(lobby.getCurrentTickIndex()).getTimestamp())
                                .build()
                );
                continue;
            }

            lobby.setCurrentTickIndex(nextTick);
            lobby.setLastTickTime(LocalDateTime.now());
            lobbyRepository.save(lobby);

            MarketCandle currentCandle = candles.get(nextTick);

            messagingTemplate.convertAndSend(
                    "/topic/lobby/" + lobby.getId(),
                    GameUpdateMessage.builder()
                            .lobbyId(lobby.getId())
                            .tickIndex(nextTick)
                            .asset(getAssetForDataset(lobby.getDataset()))
                            .open(currentCandle.getOpen())
                            .high(currentCandle.getHigh())
                            .low(currentCandle.getLow())
                            .close(currentCandle.getClose())
                            .currentPrice(currentCandle.getClose())
                            .timestamp(currentCandle.getTimestamp())
                            .build()
            );
        }
    }

    private double calculateReturnValue(Position position, double currentPrice) {
        double initialCost = position.getQuantity() * position.getEntryPrice();
        if (position.getType() == PositionType.LONG) {
            double pnl = (currentPrice - position.getEntryPrice()) * position.getQuantity() * position.getLeverage();
            return Math.max(0, initialCost + pnl);
        } else {
            double pnl = (position.getEntryPrice() - currentPrice) * position.getQuantity() * position.getLeverage();
            return Math.max(0, initialCost + pnl);
        }
    }

    private String getAssetForDataset(String dataset) {
        if (dataset.startsWith("ETH")) return "ETH";
        if (dataset.startsWith("SOL")) return "SOL";
        return "BTC";
    }
}