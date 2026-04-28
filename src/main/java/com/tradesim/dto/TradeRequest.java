package com.tradesim.dto;

import lombok.Data;

@Data
public class TradeRequest {
    private String asset;
    private double quantity;
    private int leverage;
    private String type;
}
