package com.tradesim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class LobbyResponse {
    private Long id;
    private String name;
    private int maxPlayers;
    private int currentPlayers;
    private double startBalance;
    private String dataset;
    private String gameMode;
    private String status;
    private int maxLeverage;
    private String creatorUsername;

    private int currentTickIndex;
}