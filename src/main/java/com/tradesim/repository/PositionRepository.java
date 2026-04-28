package com.tradesim.repository;

import com.tradesim.entity.Portfolio;
import com.tradesim.entity.Position;
import com.tradesim.entity.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByPortfolioAndStatus(Portfolio portfolio, PositionStatus status);
    List<Position> findByPortfolio(Portfolio portfolio);
}