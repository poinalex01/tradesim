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


    private static final Map<String, List<String>> DATASET_POOL = Map.of(
            "SCALPING", List.of("BTC_SCALP_1", "BTC_SCALP_2", "BTC_SCALP_3", "ETH_SCALP_1", "ETH_SCALP_2"),
            "DAY_TRADING", List.of("BTC_DAY_1", "BTC_DAY_2", "BTC_DAY_3", "BTC_DAY_4", "ETH_DAY_1"),
            "SWING_TRADING", List.of("BTC_SWING_1", "BTC_SWING_2", "BTC_SWING_3", "ETH_SWING_1", "ETH_SWING_2")
    );

    private static final Map<String, Integer> GAMEMODE_TICK_INTERVAL = Map.of(
            "SCALPING", 2,
            "DAY_TRADING", 5,
            "SWING_TRADING", 8
    );

    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final MarketCandleRepository marketCandleRepository;

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

        String dataset = pickRandomDataset(lobby.getGameMode());
        String asset = getAssetForDataset(dataset);

        List<MarketCandle> candles = marketCandleRepository
                .findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);

        long contextCount = candles.stream().filter(MarketCandle::isContextCandle).count();

        String interval = candles.isEmpty() ? "1d" : candles.get(0).getInterval();

        lobby.setDataset(dataset);
        lobby.setAsset(asset);
        lobby.setCandleInterval(interval);
        lobby.setContextCandleCount((int) contextCount);
        lobby.setStatus(LobbyStatus.RUNNING);
        lobby.setStartedAt(LocalDateTime.now());
        lobby.setCurrentTickIndex((int) contextCount);
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

    private String pickRandomDataset(String gameMode) {
        List<String> pool = DATASET_POOL.get(gameMode);
        if (pool == null) throw new RuntimeException("Unknown gameMode: " + gameMode);
        return pool.get(new java.util.Random().nextInt(pool.size()));
    }

    private String getAssetForDataset(String dataset) {
        if (dataset.startsWith("ETH")) return "ETH";
        if (dataset.startsWith("SOL")) return "SOL";
        return "BTC";
    }
}
