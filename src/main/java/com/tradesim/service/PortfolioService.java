package com.tradesim.service;

import com.tradesim.dto.PortfolioResponse;
import com.tradesim.dto.PositionResponse;
import com.tradesim.entity.*;
import com.tradesim.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final LobbyRepository lobbyRepository;
    private final MarketDataService marketDataService;
    private final GameEngineService gameEngineService;

    public Portfolio getOrCreatePortfolio(User user, Lobby lobby) {
        Optional<Portfolio> existing = portfolioRepository.findByUserAndLobby(user, lobby);
        if (existing.isPresent()) return existing.get();

        try {
            Portfolio p = Portfolio.builder()
                    .user(user)
                    .lobby(lobby)
                    .cashBalance(lobby.getStartBalance())
                    .startBalance(lobby.getStartBalance())
                    .build();
            return portfolioRepository.save(p);
        } catch (Exception e) {
            return portfolioRepository.findByUserAndLobby(user, lobby)
                    .orElseThrow(() -> new RuntimeException("Could not create portfolio"));
        }
    }

    public PortfolioResponse getPortfolio(String username, Long lobbyId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        Portfolio portfolio = getOrCreatePortfolio(user, lobby);
        List<Position> openPositions = positionRepository.findByPortfolioAndStatus(portfolio, PositionStatus.OPEN);

        double positionsValue = openPositions.stream()
                .mapToDouble(pos -> calculatePositionValue(pos, lobby))
                .sum();

        double totalValue = portfolio.getCashBalance() + positionsValue;
        double pnl = totalValue - portfolio.getStartBalance();
        double pnlPercent = (pnl / portfolio.getStartBalance()) * 100;

        List<PositionResponse> positionResponses = openPositions.stream()
                .map(pos -> toPositionResponse(pos, lobby))
                .toList();

        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .username(username)
                .cashBalance(portfolio.getCashBalance())
                .startBalance(portfolio.getStartBalance())
                .totalValue(totalValue)
                .profitLoss(pnl)
                .profitLossPercent(pnlPercent)
                .openPositions(positionResponses)
                .build();
    }

    private double calculatePositionValue(Position position, Lobby lobby) {
        double currentPrice = gameEngineService.getLivePrice(
                lobby.getId(), position.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        if (position.getType() == PositionType.LONG) {
            return position.getQuantity() * currentPrice;
        } else {
            double pnl = (position.getEntryPrice() - currentPrice) * position.getQuantity() * position.getLeverage();
            return position.getEntryPrice() * position.getQuantity() + pnl;
        }
    }

    private PositionResponse toPositionResponse(Position position, Lobby lobby) {
        double currentPrice = gameEngineService.getLivePrice(
                lobby.getId(), position.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        double pnl;
        if (position.getType() == PositionType.LONG) {
            pnl = (currentPrice - position.getEntryPrice()) * position.getQuantity() * position.getLeverage();
        } else {
            pnl = (position.getEntryPrice() - currentPrice) * position.getQuantity() * position.getLeverage();
        }

        return PositionResponse.builder()
                .id(position.getId())
                .asset(position.getAsset())
                .quantity(position.getQuantity())
                .entryPrice(position.getEntryPrice())
                .currentPrice(currentPrice)
                .pnl(pnl)
                .leverage(position.getLeverage())
                .type(position.getType().name())
                .status(position.getStatus().name())
                .build();
    }
}