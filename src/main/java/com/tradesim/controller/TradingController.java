package com.tradesim.controller;

import com.tradesim.dto.PositionResponse;
import com.tradesim.dto.TradeRequest;
import com.tradesim.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    @PostMapping("/{lobbyId}/open")
    public ResponseEntity<PositionResponse> openPosition(
            @PathVariable Long lobbyId,
            @RequestBody TradeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tradingService.openPosition(userDetails.getUsername(), lobbyId, request));
    }

    @PostMapping("/{lobbyId}/close/{positionId}")
    public ResponseEntity<PositionResponse> closePosition(
            @PathVariable Long lobbyId,
            @PathVariable Long positionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tradingService.closePosition(userDetails.getUsername(), lobbyId, positionId));
    }
}
