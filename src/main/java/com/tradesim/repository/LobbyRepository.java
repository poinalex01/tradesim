package com.tradesim.repository;

import com.tradesim.entity.Lobby;
import com.tradesim.entity.LobbyStatus;
import com.tradesim.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Long> {
    List<Lobby> findByStatus(LobbyStatus status);
    @Query("SELECT l FROM Lobby l JOIN l.players p WHERE p = :user AND l.status = 'RUNNING' AND l.gameMode = :gameMode")
    Optional<Lobby> findActiveByUserAndGameMode(@Param("user") User user, @Param("gameMode") String gameMode);
}
