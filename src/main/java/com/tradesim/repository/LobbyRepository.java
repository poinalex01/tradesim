package com.tradesim.repository;

import com.tradesim.entity.Lobby;
import com.tradesim.entity.LobbyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Long> {
    List<Lobby> findByStatus(LobbyStatus status);
}
