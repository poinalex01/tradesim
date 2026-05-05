package com.tradesim.service;

import com.tradesim.dto.PortfolioResponse;
import com.tradesim.dto.PositionResponse;
import com.tradesim.entity.*;
import com.tradesim.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final LobbyRepository lobbyRepository;
    private final MarketDataService marketDataService;

    public Portfolio getOrCreatePortfolio(User user, Lobby lobby) {
        return portfolioRepository.findByUserAndLobby(user, lobby)
                .orElseGet(() -> {
                    Portfolio existing = portfolioRepository.findByUserAndLobby(user, lobby).orElse(null);
                    if (existing != null) return existing;

                    Portfolio p = Portfolio.builder()
                            .user(user)
                            .lobby(lobby)
                            .cashBalance(lobby.getStartBalance())
                            .startBalance(lobby.getStartBalance())
                            .build();
                    return portfolioRepository.save(p);
                });
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
        double currentPrice = marketDataService.getCurrentPrice(
                position.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        if (position.getType() == PositionType.LONG) {
            return position.getQuantity() * currentPrice;
        } else {
            double pnl = (position.getEntryPrice() - currentPrice) * position.getQuantity() * position.getLeverage();
            double initialValue = position.getEntryPrice() * position.getQuantity();
            return initialValue + pnl;
        }
    }

    private PositionResponse toPositionResponse(Position position, Lobby lobby) {
        double currentPrice = marketDataService.getCurrentPrice(
                position.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
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