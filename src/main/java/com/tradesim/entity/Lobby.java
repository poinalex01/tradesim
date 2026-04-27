package com.tradesim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lobbies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lobby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int maxPlayers;

    @Column(nullable = false)
    private double startBalance;

    @Column(nullable = false)
    private String dataset;

    @Column(nullable = false)
    private String gameMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LobbyStatus status;

    @Column(nullable = false)
    private int maxLeverage;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "lobby_players",
            joinColumns = @JoinColumn(name = "lobby_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> players = new ArrayList<>();
}