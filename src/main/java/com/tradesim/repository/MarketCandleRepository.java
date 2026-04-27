package com.tradesim.repository;

import com.tradesim.entity.MarketCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {
    List<MarketCandle> findByDatasetAndAssetOrderByTimestampAsc(String dataset, String asset);
    boolean existsByDatasetAndAsset(String dataset, String asset);
}
