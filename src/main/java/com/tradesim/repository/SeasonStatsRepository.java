package com.tradesim.repository;

import com.tradesim.entity.Season;
import com.tradesim.entity.SeasonStats;
import com.tradesim.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonStatsRepository extends JpaRepository<SeasonStats, Long> {
    Optional<SeasonStats> findByUserAndSeason(User user, Season season);
    List<SeasonStats> findBySeasonOrderByTotalProfitDesc(Season season);
    List<SeasonStats> findBySeason(Season season);
    Optional<SeasonStats> findByUserAndSeasonAndGameMode(User user, Season season, String gameMode);
    List<SeasonStats> findBySeasonAndGameModeOrderByTotalProfitDesc(Season season, String gameMode);
}
