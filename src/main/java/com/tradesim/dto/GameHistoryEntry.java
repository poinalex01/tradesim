package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class GameHistoryEntry {
    private Long lobbyId;
    private String lobbyName;
    private String dataset;
    private String gameMode;
    private double startBalance;
    private double finalValue;
    private double profitLoss;
    private double profitLossPercent;
    private int placement;
    private int totalPlayers;
    private LocalDateTime playedAt;
}
