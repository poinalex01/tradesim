package com.tradesim.service;

import com.tradesim.dto.PositionResponse;
import com.tradesim.dto.TradeRequest;
import com.tradesim.entity.*;
import com.tradesim.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TradingService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;

    public PositionResponse openPosition(String username, Long lobbyId, TradeRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        Portfolio portfolio = portfolioService.getOrCreatePortfolio(user, lobby);

        double currentPrice = marketDataService.getCurrentPrice(
                request.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        if (currentPrice == 0) throw new RuntimeException("No price data for asset");

        int leverage = Math.min(request.getLeverage(), lobby.getMaxLeverage());
        double cost = request.getQuantity() * currentPrice;

        if (cost > portfolio.getCashBalance()) {
            throw new RuntimeException("Insufficient balance");
        }

        portfolio.setCashBalance(portfolio.getCashBalance() - cost);
        portfolioRepository.save(portfolio);

        PositionType type = PositionType.valueOf(request.getType().toUpperCase());

        Position position = Position.builder()
                .portfolio(portfolio)
                .asset(request.getAsset().toUpperCase())
                .quantity(request.getQuantity())
                .entryPrice(currentPrice)
                .leverage(leverage)
                .type(type)
                .status(PositionStatus.OPEN)
                .build();

        positionRepository.save(position);

        return toPositionResponse(position, currentPrice);
    }

    public PositionResponse closePosition(String username, Long lobbyId, Long positionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        Portfolio portfolio = portfolioRepository.findByUserAndLobby(user, lobby)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("Position not found"));

        if (!position.getPortfolio().getId().equals(portfolio.getId())) {
            throw new RuntimeException("Position does not belong to you");
        }
        if (position.getStatus() == PositionStatus.CLOSED) {
            throw new RuntimeException("Position already closed");
        }

        double currentPrice = marketDataService.getCurrentPrice(
                position.getAsset(), lobby.getDataset(), lobby.getCurrentTickIndex());
        double returnValue = calculateReturnValue(position, currentPrice);

        portfolio.setCashBalance(portfolio.getCashBalance() + returnValue);
        portfolioRepository.save(portfolio);

        position.setStatus(PositionStatus.CLOSED);
        position.setClosedAt(LocalDateTime.now());
        positionRepository.save(position);

        return toPositionResponse(position, currentPrice);
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

    private PositionResponse toPositionResponse(Position position, double currentPrice) {
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