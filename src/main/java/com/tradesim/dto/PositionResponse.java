package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class PositionResponse {
    private Long id;
    private String asset;
    private double quantity;
    private double entryPrice;
    private double currentPrice;
    private double pnl;
    private int leverage;
    private String type;
    private String status;
}