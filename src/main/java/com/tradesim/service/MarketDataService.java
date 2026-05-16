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
        // Day Trading datasets
        loadIfMissing("BTC", "BTC_DAY_1", "1d", 1609459200, 1617235200, 60);
        loadIfMissing("BTC", "BTC_DAY_2", "1d", 1580515200, 1588291200, 60);
        loadIfMissing("BTC", "BTC_DAY_3", "1d", 1640995200, 1672531200, 60);
        loadIfMissing("BTC", "BTC_DAY_4", "1d", 1625097600, 1632960000, 60);
        loadIfMissing("ETH", "ETH_DAY_1", "1d", 1617235200, 1625097600, 60);

        // Scalping datasets (hourly)
        loadIfMissing("BTC", "BTC_SCALP_1", "1h", 1635724800, 1636934400, 96);
        loadIfMissing("BTC", "BTC_SCALP_2", "1h", 1620172800, 1621382400, 96);
        loadIfMissing("BTC", "BTC_SCALP_3", "1h", 1583712000, 1584921600, 96);
        loadIfMissing("ETH", "ETH_SCALP_1", "1h", 1635724800, 1636934400, 96);
        loadIfMissing("ETH", "ETH_SCALP_2", "1h", 1620172800, 1621382400, 96);

        // Swing Trading datasets (weekly)
        loadIfMissing("BTC", "BTC_SWING_1", "1w", 1577836800, 1622505600, 24);
        loadIfMissing("BTC", "BTC_SWING_2", "1w", 1609459200, 1654041600, 24);
        loadIfMissing("BTC", "BTC_SWING_3", "1w", 1514764800, 1551398400, 24);
        loadIfMissing("ETH", "ETH_SWING_1", "1w", 1577836800, 1622505600, 24);
        loadIfMissing("ETH", "ETH_SWING_2", "1w", 1609459200, 1654041600, 24);
    }

    private void loadIfMissing(String asset, String dataset, String interval, long from, long to, int contextCount) {
        if (!marketCandleRepository.existsByDatasetAndAsset(dataset, asset)) {
            try {
                System.out.println("Loading market data: " + dataset);
                loadDataset(asset, dataset, interval, from, to, contextCount);
            } catch (Exception e) {
                System.out.println("Failed to load " + dataset + ": " + e.getMessage());
            }
        }
    }

    public String loadDataset(String asset, String dataset, String interval, long fromTimestamp, long toTimestamp, int contextCount) {
        if (marketCandleRepository.existsByDatasetAndAsset(dataset, asset)) {
            return "Dataset already exists: " + dataset;
        }

        String symbol = asset.toUpperCase() + "USDT";
        String url = String.format(
                "https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=1000",
                symbol, interval, fromTimestamp * 1000, toTimestamp * 1000
        );

        WebClient client = webClientBuilder.build();
        List<List<Object>> response = client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {})
                .block();

        List<MarketCandle> candles = new ArrayList<>();
        for (int i = 0; i < response.size(); i++) {
            List<Object> k = response.get(i);
            long ts = ((Number) k.get(0)).longValue();
            double open = Double.parseDouble((String) k.get(1));
            double high = Double.parseDouble((String) k.get(2));
            double low = Double.parseDouble((String) k.get(3));
            double close = Double.parseDouble((String) k.get(4));
            double volume = Double.parseDouble((String) k.get(5));

            MarketCandle candle = MarketCandle.builder()
                    .asset(asset.toUpperCase())
                    .dataset(dataset)
                    .interval(interval)
                    .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()))
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .contextCandle(i < contextCount)
                    .build();

            candles.add(candle);
        }

        marketCandleRepository.saveAll(candles);
        return "Loaded " + candles.size() + " candles for " + asset + " / " + dataset;
    }

    public double getCurrentPrice(String asset, String dataset, int tickIndex) {
        List<MarketCandle> candles = marketCandleRepository
                .findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);
        if (candles.isEmpty()) return 0;
        int index = Math.min(tickIndex, candles.size() - 1);
        return candles.get(index).getClose();
    }

    public List<MarketCandle> getCandles(String dataset, String asset) {
        return marketCandleRepository.findByDatasetAndAssetOrderByTimestampAsc(dataset, asset);
    }
}