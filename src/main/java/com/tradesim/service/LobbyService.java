package com.tradesim.service;

import com.tradesim.dto.CreateLobbyRequest;
import com.tradesim.dto.LobbyResponse;
import com.tradesim.dto.PortfolioResponse;
import com.tradesim.entity.*;
import com.tradesim.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LobbyService {
    private static final int START_BALANCE = 10000;

    private final GameEngineService gameEngineService;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final MarketCandleRepository marketCandleRepository;

    private static final Map<String, String> GAMEMODE_INTERVAL = Map.of(
            "SCALPING", "1h",
            "DAY_TRADING", "1d",
            "SWING_TRADING", "1w"
    );

    private static final Map<String, Integer> CONTEXT_CANDLES = Map.of(
            "SCALPING", 96,
            "DAY_TRADING", 60,
            "SWING_TRADING", 24
    );

    private static final List<String> ASSETS = List.of("BTC", "ETH");

    private long[] getRandomTimeRange(String gameMode) {
        java.util.Random rand = new java.util.Random();
        long minTime = 1514764800L;
        long maxTime = 1704067200L;

        long duration = switch (gameMode) {
            case "SCALPING" -> 7 * 24 * 3600L;
            case "DAY_TRADING" -> 90 * 24 * 3600L;
            case "SWING_TRADING" -> 52 * 7 * 24 * 3600L;
            default -> 90 * 24 * 3600L;
        };

        long start = minTime + (long)(rand.nextDouble() * (maxTime - duration - minTime));
        return new long[]{start, start + duration};
    }

    public LobbyResponse createLobby(CreateLobbyRequest request, String username) {
        User creator = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        checkPlayerNotAlreadyInActiveGame(creator, request.getGameMode());

        Lobby lobby = Lobby.builder()
                .name(request.getName())
                .maxPlayers(request.getMaxPlayers())
                .startBalance(START_BALANCE)
                .dataset("PENDING")
                .asset("PENDING")
                .candleInterval("1d")
                .contextCandleCount(0)
                .gameMode(request.getGameMode())
                .maxLeverage(request.getMaxLeverage())
                .status(LobbyStatus.WAITING)
                .creator(creator)
                .players(new ArrayList<>(List.of(creator)))
                .build();

        lobbyRepository.save(lobby);
        return toResponse(lobby);
    }

    public LobbyResponse joinLobby(Long lobbyId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        if (lobby.getStatus() != LobbyStatus.WAITING) {
            throw new RuntimeException("Lobby is not open");
        }
        if (lobby.getPlayers().size() >= lobby.getMaxPlayers()) {
            throw new RuntimeException("Lobby is full");
        }
        if (lobby.getPlayers().contains(user)) {
            throw new RuntimeException("Already in lobby");
        }

        checkPlayerNotAlreadyInActiveGame(user,lobby.getGameMode());
        lobby.getPlayers().add(user);
        lobbyRepository.save(lobby);
        return toResponse(lobby);
    }

    public List<LobbyResponse> getWaitingLobbies() {
        return lobbyRepository.findByStatus(LobbyStatus.WAITING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public LobbyResponse getLobby(Long lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));
        return toResponse(lobby);
    }

    private LobbyResponse toResponse(Lobby lobby) {
        return LobbyResponse.builder()
                .id(lobby.getId())
                .name(lobby.getName())
                .maxPlayers(lobby.getMaxPlayers())
                .currentPlayers(lobby.getPlayers().size())
                .startBalance(lobby.getStartBalance())
                .dataset(lobby.getDataset())
                .gameMode(lobby.getGameMode())
                .status(lobby.getStatus().name())
                .maxLeverage(lobby.getMaxLeverage())
                .creatorUsername(lobby.getCreator().getUsername())
                .currentTickIndex(lobby.getCurrentTickIndex())
                .build();
    }

    public LobbyResponse startLobby(Long lobbyId, String username) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        if (!lobby.getCreator().getUsername().equals(username)) {
            throw new RuntimeException("Only the creator can start the lobby");
        }
        if (lobby.getStatus() != LobbyStatus.WAITING) {
            throw new RuntimeException("Lobby already started");
        }

        String gameMode = lobby.getGameMode();
        String asset = ASSETS.get(new java.util.Random().nextInt(ASSETS.size()));
        String interval = GAMEMODE_INTERVAL.get(gameMode);
        int contextCount = CONTEXT_CANDLES.get(gameMode);
        long[] timeRange = getRandomTimeRange(gameMode);
        String dataset = asset + "_" + gameMode + "_" + timeRange[0];

        marketDataService.loadDataset(asset, dataset, interval, timeRange[0], timeRange[1], contextCount);

        List<MarketCandle> candles = marketCandleRepository
                .findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);

        long contextCandleCount = candles.stream().filter(MarketCandle::isContextCandle).count();

        lobby.setDataset(dataset);
        lobby.setAsset(asset);
        lobby.setCandleInterval(interval);
        lobby.setContextCandleCount((int) contextCandleCount);
        lobby.setStatus(LobbyStatus.RUNNING);
        lobby.setStartedAt(LocalDateTime.now());
        lobby.setCurrentTickIndex((int) contextCandleCount);
        lobbyRepository.save(lobby);
        return toResponse(lobby);
    }

    public List<LobbyResponse> getAllLobbies() {
        return lobbyRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PortfolioResponse> getLeaderBoard(Long lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId).orElseThrow(() -> new RuntimeException("Lobby not found"));
        List<Portfolio> portfolios = portfolioRepository.findByLobby(lobby);

        return portfolios.stream().map(p -> {
            List<Position> openPositions = positionRepository.findByPortfolioAndStatus(p, PositionStatus.OPEN);
            double positionsValue = openPositions.stream()
                    .mapToDouble(pos -> calculatePositionValue(pos, lobby))
                    .sum();
            double totalValue = p.getCashBalance() + positionsValue;
            double pnl = totalValue - p.getStartBalance();
            double pnlPercent = (pnl / p.getStartBalance()) * 100;

            return PortfolioResponse.builder()
                    .id(p.getId())
                    .username(p.getUser().getUsername())
                    .cashBalance(p.getCashBalance())
                    .startBalance(p.getStartBalance())
                    .totalValue(totalValue)
                    .profitLoss(pnl)
                    .profitLossPercent(pnlPercent)
                    .openPositions(List.of())
                    .build();
        }).toList();
    }

    private double calculatePositionValue(Position pos, Lobby lobby) {
        double currentPrice = gameEngineService.getLivePrice(
                lobby.getId(), pos.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        if (pos.getType() == PositionType.LONG) {
            return pos.getQuantity() * currentPrice;
        } else {
            double pnl = (pos.getEntryPrice() - currentPrice) * pos.getQuantity() * pos.getLeverage();
            return pos.getEntryPrice() * pos.getQuantity() + pnl;
        }
    }

    private void checkPlayerNotAlreadyInActiveGame(User user, String gameMode) {
        lobbyRepository.findActiveByUserAndGameMode(user, gameMode).ifPresent(l -> {
            throw new RuntimeException("You already have an active " + gameMode + " lobby");
        });
    }
}
