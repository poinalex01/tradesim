package com.tradesim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false)
    private String asset;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private double entryPrice;

    @Column(nullable = false)
    private int leverage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;

    private LocalDateTime openedAt = LocalDateTime.now();
    private LocalDateTime closedAt;
}