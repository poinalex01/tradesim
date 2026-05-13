package com.tradesim.service;

import com.tradesim.entity.MarketCandle;
import com.tradesim.repository.MarketCandleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketCandleRepository marketCandleRepository;
    private final WebClient.Builder webClientBuilder;

    @PostConstruct
    public void initMarketData() {
        loadIfMissing("BTC", "BTC_2021_Q1", 1609459200, 1617235200);
        loadIfMissing("BTC", "BTC_2020_COVID", 1580515200, 1588291200);
        loadIfMissing("BTC", "BTC_2022_BEAR", 1640995200, 1672531200);
        loadIfMissing("BTC", "BTC_2021_Q3", 1625097600, 1632960000);
        loadIfMissing("ETH", "ETH_2021_Q2", 1617235200, 1625097600);
    }
    private void loadIfMissing(String asset, String dataset, long from, long to) {
        if (!marketCandleRepository.existsByDatasetAndAsset(dataset, asset)) {
            try {
                System.out.println("Loading market data: " + dataset);
                loadDataset(asset, dataset, from, to);
            } catch (Exception e) {
                System.out.println("Failed to load " + dataset + ": " + e.getMessage());
            }
        }
    }

    public List<MarketCandle> getCandles(String dataset, String asset) {
        return marketCandleRepository.findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);
    }

    public double getCurrentPrice(String asset, String dataset, int tickIndex) {
        List<MarketCandle> candles = marketCandleRepository
                .findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);
        if (candles.isEmpty()) return 0;
        int index = Math.min(tickIndex, candles.size() - 1);
        return candles.get(index).getClose();
    }

    public String loadDataset(String asset, String dataset, long fromTimestamp, long toTimestamp) {
        if (marketCandleRepository.existsByDatasetAndAsset(dataset, asset)) {
            return "Dataset already exists: " + dataset;
        }

        String symbol = asset.toUpperCase() + "USDT";

        String url = String.format(
                "https://api.binance.com/api/v3/klines?symbol=%s&interval=1d&startTime=%d&endTime=%d&limit=1000",
                symbol, fromTimestamp * 1000, toTimestamp * 1000
        );

        WebClient client = webClientBuilder.build();

        List<List<Object>> response = client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {})
                .block();

        List<MarketCandle> candles = new ArrayList<>();

        for (List<Object> k : response) {
            long ts = ((Number) k.get(0)).longValue();
            double open = Double.parseDouble((String) k.get(1));
            double high = Double.parseDouble((String) k.get(2));
            double low = Double.parseDouble((String) k.get(3));
            double close = Double.parseDouble((String) k.get(4));
            double volume = Double.parseDouble((String) k.get(5));

            MarketCandle candle = MarketCandle.builder()
                    .asset(asset.toUpperCase())
                    .dataset(dataset)
                    .timestamp(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(ts),
                            ZoneId.systemDefault()))
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build();

            candles.add(candle);
        }

        marketCandleRepository.saveAll(candles);
        return "Loaded " + candles.size() + " candles for " + asset + " / " + dataset;
    }
}