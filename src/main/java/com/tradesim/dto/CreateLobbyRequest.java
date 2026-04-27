package com.tradesim.dto;

import lombok.Data;

@Data
public class CreateLobbyRequest {
    private String name;
    private int maxPlayers;
    private double startBalance;
    private String dataset;
    private String gameMode;
    private int maxLeverage;
}