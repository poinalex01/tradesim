package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class ProfileResponse {
    private String username;
    private int gamesPlayed;
    private int wins;
    private double winRate;
    private double totalProfitLoss;
    private double bestPortfolioValue;
    private List<GameHistoryEntry> gameHistory;
}
