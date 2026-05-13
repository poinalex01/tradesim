package com.tradesim.service;

import com.tradesim.dto.GameUpdateMessage;
import com.tradesim.entity.Lobby;
import com.tradesim.entity.LobbyStatus;
import com.tradesim.entity.MarketCandle;
import com.tradesim.entity.Portfolio;
import com.tradesim.repository.LobbyRepository;
import com.tradesim.repository.MarketCandleRepository;
import com.tradesim.repository.PortfolioRepository;
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

    private String getAssetForDataset(String dataset) {
        if (dataset.startsWith("ETH")) return "ETH";
        if (dataset.startsWith("SOL")) return "SOL";
        return "BTC";
    }
}