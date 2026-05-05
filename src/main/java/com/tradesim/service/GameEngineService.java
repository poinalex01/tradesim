package com.tradesim.service;

import com.tradesim.dto.GameUpdateMessage;
import com.tradesim.entity.Lobby;
import com.tradesim.entity.LobbyStatus;
import com.tradesim.entity.MarketCandle;
import com.tradesim.repository.LobbyRepository;
import com.tradesim.repository.MarketCandleRepository;
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
                    .findByDatasetAndAssetOrderByTimestampAsc(lobby.getDataset(), "BTC");

            if (candles.isEmpty()) continue;

            int nextTick = lobby.getCurrentTickIndex() + 1;

            if (nextTick >= candles.size()) {
                lobby.setStatus(LobbyStatus.FINISHED);
                lobby.setEndedAt(LocalDateTime.now());
                lobbyRepository.save(lobby);

                messagingTemplate.convertAndSend(
                        "/topic/lobby/" + lobby.getId(),
                        GameUpdateMessage.builder()
                                .lobbyId(lobby.getId())
                                .tickIndex(lobby.getCurrentTickIndex())
                                .asset("BTC")
                                .currentPrice(candles.get(lobby.getCurrentTickIndex()).getClose())
                                .timestamp(LocalDateTime.now())
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
                            .asset("BTC")
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
}