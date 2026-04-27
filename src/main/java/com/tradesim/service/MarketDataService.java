package com.tradesim.service;

import com.tradesim.entity.MarketCandle;
import com.tradesim.repository.MarketCandleRepository;
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

    private static final Map<String, String> ASSET_IDS = Map.of(
            "BTC", "bitcoin",
            "ETH", "ethereum",
            "SOL", "solana"
    );

    public List<MarketCandle> getCandles(String dataset, String asset) {
        return marketCandleRepository.findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);
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
