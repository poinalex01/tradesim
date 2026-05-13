package com.tradesim.controller;

import com.tradesim.dto.SeasonResponse;
import com.tradesim.service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/season")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @GetMapping("/current")
    public ResponseEntity<SeasonResponse> getCurrentSeason(
            @RequestParam String gameMode,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(seasonService.getCurrentSeasonStandings(userDetails.getUsername(), gameMode));
    }
}