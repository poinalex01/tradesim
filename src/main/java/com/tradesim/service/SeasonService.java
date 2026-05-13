package com.tradesim.service;

import com.tradesim.dto.SeasonResponse;
import com.tradesim.dto.SeasonStandingResponse;
import com.tradesim.entity.Season;
import com.tradesim.entity.SeasonStats;
import com.tradesim.entity.User;
import com.tradesim.repository.SeasonRepository;
import com.tradesim.repository.SeasonStatsRepository;
import com.tradesim.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final SeasonStatsRepository seasonStatsRepository;
    private final UserRepository userRepository;

    @PostConstruct
    public void initSeason() {
        if (seasonRepository.findByActiveTrue().isEmpty()) {
            createNewSeason();
        }
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void checkSeasonReset() {
        Season current = seasonRepository.findByActiveTrue().orElse(null);
        if (current != null && LocalDateTime.now().isAfter(current.getEndDate())) {
            current.setActive(false);
            seasonRepository.save(current);
            createNewSeason();
        }
    }

    private void createNewSeason() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = start.plusMonths(1);

        String name = now.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                + " " + now.getYear();

        Season season = Season.builder()
                .name(name)
                .startDate(start)
                .endDate(end)
                .active(true)
                .build();

        seasonRepository.save(season);
    }

    public Season getCurrentSeason() {
        return seasonRepository.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("No active season"));
    }

    public void updateSeasonStats(User user, double profit, boolean won, String gameMode) {
        Season season = getCurrentSeason();

        SeasonStats stats = seasonStatsRepository.findByUserAndSeasonAndGameMode(user, season, gameMode)
                .orElseGet(() -> SeasonStats.builder()
                        .user(user)
                        .season(season)
                        .gameMode(gameMode)
                        .totalProfit(0)
                        .gamesPlayed(0)
                        .wins(0)
                        .build());

        stats.setTotalProfit(stats.getTotalProfit() + profit);
        stats.setGamesPlayed(stats.getGamesPlayed() + 1);
        if (won) stats.setWins(stats.getWins() + 1);
        stats.setLastUpdated(LocalDateTime.now());

        seasonStatsRepository.save(stats);
        recalculateRanks(season, gameMode);
    }

    private void recalculateRanks(Season season, String gameMode) {
        List<SeasonStats> allStats = seasonStatsRepository
                .findBySeasonAndGameModeOrderByTotalProfitDesc(season, gameMode);
        int total = allStats.size();
        if (total == 0) return;

        for (int i = 0; i < total; i++) {
            double percentile = (double) (i + 1) / total * 100;
            allStats.get(i).setRank(getRank(percentile));
        }

        seasonStatsRepository.saveAll(allStats);
    }

    private String getRank(double percentile) {
        if (percentile <= 1) return "RADIANT";
        if (percentile <= 3) return "IMMORTAL";
        if (percentile <= 10) return "ASCENDANT";
        if (percentile <= 20) return "DIAMOND";
        if (percentile <= 35) return "PLATINUM";
        if (percentile <= 50) return "GOLD";
        if (percentile <= 70) return "SILVER";
        if (percentile <= 85) return "BRONZE";
        return "IRON";
    }

    public SeasonResponse getCurrentSeasonStandings(String username, String gameMode) {
        Season season = getCurrentSeason();
        List<SeasonStats> allStats = seasonStatsRepository
                .findBySeasonAndGameModeOrderByTotalProfitDesc(season, gameMode);

        List<SeasonStandingResponse> standings = new ArrayList<>();
        SeasonStandingResponse myStats = null;

        for (int i = 0; i < allStats.size(); i++) {
            SeasonStats s = allStats.get(i);
            SeasonStandingResponse standing = SeasonStandingResponse.builder()
                    .username(s.getUser().getUsername())
                    .totalProfit(s.getTotalProfit())
                    .gamesPlayed(s.getGamesPlayed())
                    .wins(s.getWins())
                    .rank(s.getRank() != null ? s.getRank() : "IRON")
                    .position(i + 1)
                    .build();

            standings.add(standing);
            if (s.getUser().getUsername().equals(username)) {
                myStats = standing;
            }
        }

        if (myStats == null) {
            myStats = SeasonStandingResponse.builder()
                    .username(username)
                    .totalProfit(0)
                    .gamesPlayed(0)
                    .wins(0)
                    .rank("IRON")
                    .position(allStats.size() + 1)
                    .build();
        }

        return SeasonResponse.builder()
                .id(season.getId())
                .name(season.getName())
                .startDate(season.getStartDate())
                .endDate(season.getEndDate())
                .active(true)
                .standings(standings)
                .myStats(myStats)
                .build();
    }
}
