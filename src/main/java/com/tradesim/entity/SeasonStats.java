package com.tradesim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "season_stats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "season_id", "game_mode"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    // @Column(nullable = false)
    // private String gameMode;
    @Column(nullable = false, columnDefinition = "varchar(255) default 'NORMAL'")
    private String gameMode;

    @Column(nullable = false)
    private double totalProfit = 0;

    @Column(nullable = false)
    private int gamesPlayed = 0;

    @Column(nullable = false)
    private int wins = 0;

    private String rank;

    private LocalDateTime lastUpdated;
}
