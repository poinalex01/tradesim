package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class PortfolioResponse {
    private Long id;
    private String username;
    private double cashBalance;
    private double startBalance;
    private double totalValue;
    private double profitLoss;
    private double profitLossPercent;
    private List<PositionResponse> openPositions;
}