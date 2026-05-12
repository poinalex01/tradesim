package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class SeasonStandingResponse {
    private String username;
    private double totalProfit;
    private int gamesPlayed;
    private int wins;
    private String rank;
    private int position;
}
