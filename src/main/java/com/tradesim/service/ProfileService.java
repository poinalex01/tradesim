package com.tradesim.service;

import com.tradesim.dto.GameHistoryEntry;
import com.tradesim.dto.ProfileResponse;
import com.tradesim.entity.*;
import com.tradesim.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final LobbyRepository lobbyRepository;

    public ProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Portfolio> portfolios = portfolioRepository.findByUser(user);

        List<GameHistoryEntry> history = new ArrayList<>();
        int wins = 0;
        double totalPnl = 0;
        double bestValue = 0;

        for (Portfolio portfolio : portfolios) {
            Lobby lobby = portfolio.getLobby();
            if (lobby.getStatus() != LobbyStatus.FINISHED) continue;

            List<Portfolio> lobbyPortfolios = portfolioRepository.findByLobby(lobby);
            List<Portfolio> sorted = lobbyPortfolios.stream()
                    .sorted((a, b) -> Double.compare(b.getCashBalance(), a.getCashBalance()))
                    .toList();

            int placement = 1;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).getId().equals(portfolio.getId())) {
                    placement = i + 1;
                    break;
                }
            }

            if (placement == 1) wins++;

            double pnl = portfolio.getCashBalance() - portfolio.getStartBalance();
            double pnlPercent = (pnl / portfolio.getStartBalance()) * 100;
            totalPnl += pnl;

            if (portfolio.getCashBalance() > bestValue) {
                bestValue = portfolio.getCashBalance();
            }

            history.add(GameHistoryEntry.builder()
                    .lobbyId(lobby.getId())
                    .lobbyName(lobby.getName())
                    .dataset(lobby.getDataset())
                    .gameMode(lobby.getGameMode())
                    .startBalance(portfolio.getStartBalance())
                    .finalValue(portfolio.getCashBalance())
                    .profitLoss(pnl)
                    .profitLossPercent(pnlPercent)
                    .placement(placement)
                    .totalPlayers(lobbyPortfolios.size())
                    .playedAt(lobby.getEndedAt())
                    .build());
        }

        history.sort((a, b) -> b.getPlayedAt().compareTo(a.getPlayedAt()));

        double winRate = portfolios.isEmpty() ? 0 : (double) wins / history.size() * 100;

        return ProfileResponse.builder()
                .username(username)
                .gamesPlayed(history.size())
                .wins(wins)
                .winRate(winRate)
                .totalProfitLoss(totalPnl)
                .bestPortfolioValue(bestValue)
                .gameHistory(history)
                .build();
    }
}
