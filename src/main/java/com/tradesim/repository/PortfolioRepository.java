package com.tradesim.repository;

import com.tradesim.entity.Lobby;
import com.tradesim.entity.Portfolio;
import com.tradesim.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByUserAndLobby(User user, Lobby lobby);
    List<Portfolio> findByLobby(Lobby lobby);
    List<Portfolio> findByUser(User user);
}
