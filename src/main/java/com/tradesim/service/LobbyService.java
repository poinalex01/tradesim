package com.tradesim.service;

import com.tradesim.dto.CreateLobbyRequest;
import com.tradesim.dto.LobbyResponse;
import com.tradesim.dto.PortfolioResponse;
import com.tradesim.entity.*;
import com.tradesim.repository.LobbyRepository;
import com.tradesim.repository.PortfolioRepository;
import com.tradesim.repository.PositionRepository;
import com.tradesim.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LobbyService {

    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;

    public LobbyResponse createLobby(CreateLobbyRequest request, String username) {
        User creator = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Lobby lobby = Lobby.builder()
                .name(request.getName())
                .maxPlayers(request.getMaxPlayers())
                .startBalance(request.getStartBalance())
                .dataset(request.getDataset())
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

        lobby.setStatus(LobbyStatus.RUNNING);
        lobby.setStartedAt(LocalDateTime.now());
        lobby.setCurrentTickIndex(0);
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
        double currentPrice = marketDataService.getCurrentPrice(
                pos.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        if (pos.getType() == PositionType.LONG) {
            return pos.getQuantity() * currentPrice;
        } else {
            double pnl = (pos.getEntryPrice() - currentPrice) * pos.getQuantity() * pos.getLeverage();
            return pos.getEntryPrice() * pos.getQuantity() + pnl;
        }
    }
}
