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
import java.util.Map;

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

    private static final Map<String, Integer> SUBTICKS_PER_CANDLE = Map.of(
            "SCALPING", 60,
            "DAY_TRADING", 120,
            "SWING_TRADING", 240
    );

    private static final Map<String, Integer> TICK_INTERVAL_MS = Map.of(
            "SCALPING", 250,
            "DAY_TRADING", 500,
            "SWING_TRADING", 1000
    );

    private final Map<Long, Double> currentPrices = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> lastTickTimes = new java.util.concurrent.ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 100)
    public void tick() {
        List<Lobby> runningLobbies = lobbyRepository.findByStatus(LobbyStatus.RUNNING);

        for (Lobby lobby : runningLobbies) {
            long now = System.currentTimeMillis();
            long lastTick = lastTickTimes.getOrDefault(lobby.getId(), 0L);
            int intervalMs = TICK_INTERVAL_MS.getOrDefault(lobby.getGameMode(), 5000);

            if (now - lastTick < intervalMs) continue;
            lastTickTimes.put(lobby.getId(), now);

            List<MarketCandle> candles = marketCandleRepository
                    .findByDatasetAndAssetOrderByTimestampAsc(lobby.getDataset(), lobby.getAsset());

            if (candles.isEmpty()) continue;

            int subTicksPerCandle = SUBTICKS_PER_CANDLE.getOrDefault(lobby.getGameMode(), 100);
            int currentCandle = lobby.getCurrentTickIndex();
            int currentSubTick = lobby.getCurrentSubTick();

            if (currentCandle >= candles.size()) {
                finishLobby(lobby, candles);
                continue;
            }

            MarketCandle candle = candles.get(currentCandle);
            double prevClose = currentCandle > 0 ? candles.get(currentCandle - 1).getClose() : candle.getOpen();
            double interpolatedPrice = interpolate(prevClose, candle, currentSubTick, subTicksPerCandle);

            currentPrices.put(lobby.getId(), interpolatedPrice);

            int nextSubTick = currentSubTick + 1;
            boolean candleComplete = nextSubTick >= subTicksPerCandle;

            if (candleComplete) {
                lobby.setCurrentSubTick(0);
                lobby.setCurrentTickIndex(currentCandle + 1);
            } else {
                lobby.setCurrentSubTick(nextSubTick);
            }

            lobbyRepository.save(lobby);

            messagingTemplate.convertAndSend(
                    "/topic/lobby/" + lobby.getId(),
                    GameUpdateMessage.builder()
                            .lobbyId(lobby.getId())
                            .tickIndex(currentCandle)
                            .subTick(currentSubTick)
                            .asset(lobby.getAsset())
                            .open(candle.getOpen())
                            .high(candle.getHigh())
                            .low(candle.getLow())
                            .close(interpolatedPrice)
                            .currentPrice(interpolatedPrice)
                            .timestamp(candle.getTimestamp())
                            .candleComplete(candleComplete)
                            .build()
            );
        }
    }

    private double interpolate(double prevClose, MarketCandle candle, int subTick, int totalSubTicks) {
        double progress = (double) subTick / totalSubTicks;
        double targetClose = candle.getClose();
        double high = candle.getHigh();
        double low = candle.getLow();

        double trend = (targetClose - prevClose) * progress;
        double base = prevClose + trend;

        double volatility = (high - low) * 0.15;
        double noise = (Math.random() - 0.5) * 2 * volatility;

        double price = base + noise;
        price = Math.max(low, Math.min(high, price));

        return Math.round(price * 100.0) / 100.0;
    }

    private void finishLobby(Lobby lobby, List<MarketCandle> candles) {
        lobby.setStatus(LobbyStatus.FINISHED);
        lobby.setEndedAt(LocalDateTime.now());

        List<Portfolio> portfolios = portfolioRepository.findByLobby(lobby);
        for (Portfolio p : portfolios) {
            List<Position> openPositions = positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN);
            for (Position pos : openPositions) {
                double currentPrice = currentPrices.getOrDefault(lobby.getId(),
                        candles.get(candles.size() - 1).getClose());
                double returnValue = calculateReturnValue(pos, currentPrice);
                p.setCashBalance(p.getCashBalance() + returnValue);
                pos.setStatus(PositionStatus.CLOSED);
                pos.setClosedAt(LocalDateTime.now());
                positionRepository.save(pos);
            }
            portfolioRepository.save(p);
        }

        portfolios.sort((a, b) -> Double.compare(b.getCashBalance(), a.getCashBalance()));
        for (int i = 0; i < portfolios.size(); i++) {
            Portfolio p = portfolios.get(i);
            double profit = p.getCashBalance() - p.getStartBalance();
            seasonService.updateSeasonStats(p.getUser(), profit, i == 0, lobby.getGameMode());
        }

        lobbyRepository.save(lobby);

        MarketCandle last = candles.get(candles.size() - 1);
        messagingTemplate.convertAndSend(
                "/topic/lobby/" + lobby.getId(),
                GameUpdateMessage.builder()
                        .lobbyId(lobby.getId())
                        .tickIndex(candles.size() - 1)
                        .subTick(0)
                        .asset(lobby.getAsset())
                        .open(last.getOpen())
                        .high(last.getHigh())
                        .low(last.getLow())
                        .close(last.getClose())
                        .currentPrice(last.getClose())
                        .timestamp(last.getTimestamp())
                        .candleComplete(true)
                        .build()
        );

        lastTickTimes.remove(lobby.getId());
        currentPrices.remove(lobby.getId());
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
        return "BTC";
    }

    public double getLivePrice(Long lobbyId, String asset, String dataset, int tickIndex) {
        Double livePrice = currentPrices.get(lobbyId);
        if (livePrice != null && livePrice > 0) return livePrice;
        return marketDataService.getCurrentPrice(asset, dataset, tickIndex);
    }
}