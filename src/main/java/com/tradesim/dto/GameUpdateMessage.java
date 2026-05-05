package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class GameUpdateMessage {
    private Long lobbyId;
    private int tickIndex;
    private String asset;
    private double open;
    private double high;
    private double low;
    private double close;
    private double currentPrice;
    private LocalDateTime timestamp;
}