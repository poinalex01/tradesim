package com.tradesim.controller;

import com.tradesim.entity.MarketCandle;
import com.tradesim.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    @PostMapping("/load")
    public ResponseEntity<String> loadDataset(
            @RequestParam String asset,
            @RequestParam String dataset,
            @RequestParam String interval,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "0") int contextCount) {
        return ResponseEntity.ok(marketDataService.loadDataset(asset, dataset, interval, from, to, contextCount));
    }

    @GetMapping("/candles")
    public ResponseEntity<List<MarketCandle>> getCandles(
            @RequestParam String dataset,
            @RequestParam String asset) {
        return ResponseEntity.ok(marketDataService.getCandles(dataset, asset));
    }
}