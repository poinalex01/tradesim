package com.tradesim.controller;

import com.tradesim.dto.CreateLobbyRequest;
import com.tradesim.dto.LobbyResponse;
import com.tradesim.dto.PortfolioResponse;
import com.tradesim.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lobbies")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    @PostMapping
    public ResponseEntity<LobbyResponse> createLobby(
            @RequestBody CreateLobbyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lobbyService.createLobby(request, userDetails.getUsername()));
    }

    @PostMapping("/{lobbyId}/join")
    public ResponseEntity<LobbyResponse> joinLobby(
            @PathVariable Long lobbyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(lobbyService.joinLobby(lobbyId, userDetails.getUsername()));
    }

    @GetMapping("/{lobbyId}")
    public ResponseEntity<LobbyResponse> getLobby(@PathVariable Long lobbyId) {
        return ResponseEntity.ok(lobbyService.getLobby(lobbyId));
    }

    @PostMapping("/{lobbyId}/start")
    public ResponseEntity<LobbyResponse> startLobby(
            @PathVariable Long lobbyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("userDetails = " + userDetails);
        return ResponseEntity.ok(lobbyService.startLobby(lobbyId, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<LobbyResponse>> getAllLobbies() {
        return ResponseEntity.ok(lobbyService.getAllLobbies());
    }

    @GetMapping("/{lobbyId}/leaderboard")
    public ResponseEntity<List<PortfolioResponse>> getLeaderBoard(@PathVariable Long lobbyId) {
        return ResponseEntity.ok(lobbyService.getLeaderBoard(lobbyId));
    }
}