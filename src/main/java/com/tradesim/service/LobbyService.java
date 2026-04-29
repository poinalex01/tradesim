package com.tradesim.service;

import com.tradesim.dto.CreateLobbyRequest;
import com.tradesim.dto.LobbyResponse;
import com.tradesim.entity.Lobby;
import com.tradesim.entity.LobbyStatus;
import com.tradesim.entity.User;
import com.tradesim.repository.LobbyRepository;
import com.tradesim.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LobbyService {

    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;

    public LobbyResponse createLobby(CreateLobbyRequest request, String username) {
        User creator = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Lobby lobby = Lobby.builder()
                .name(request.getName())
                .maxPlayers(request.getMaxPlayers())
                .startBalance(request.getStartBalance())
                .dataset(request.getDataset())
                .gameMode(request.getGameMode())
                .maxLeverage(request.getMaxLeverage())
                .status(LobbyStatus.WAITING)
                .creator(creator)
                .players(new ArrayList<>(List.of(creator)))
                .build();

        lobbyRepository.save(lobby);
        return toResponse(lobby);
    }

    public LobbyResponse joinLobby(Long lobbyId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        if (lobby.getStatus() != LobbyStatus.WAITING) {
            throw new RuntimeException("Lobby is not open");
        }
        if (lobby.getPlayers().size() >= lobby.getMaxPlayers()) {
            throw new RuntimeException("Lobby is full");
        }
        if (lobby.getPlayers().contains(user)) {
            throw new RuntimeException("Already in lobby");
        }

        lobby.getPlayers().add(user);
        lobbyRepository.save(lobby);
        return toResponse(lobby);
    }

    public List<LobbyResponse> getWaitingLobbies() {
        return lobbyRepository.findByStatus(LobbyStatus.WAITING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public LobbyResponse getLobby(Long lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));
        return toResponse(lobby);
    }

    private LobbyResponse toResponse(Lobby lobby) {
        return LobbyResponse.builder()
                .id(lobby.getId())
                .name(lobby.getName())
                .maxPlayers(lobby.getMaxPlayers())
                .currentPlayers(lobby.getPlayers().size())
                .startBalance(lobby.getStartBalance())
                .dataset(lobby.getDataset())
                .gameMode(lobby.getGameMode())
                .status(lobby.getStatus().name())
                .maxLeverage(lobby.getMaxLeverage())
                .creatorUsername(lobby.getCreator().getUsername())
                .currentTickIndex(lobby.getCurrentTickIndex())
                .build();
    }

    public LobbyResponse startLobby(Long lobbyId, String username) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new RuntimeException("Lobby not found"));

        if (!lobby.getCreator().getUsername().equals(username)) {
            throw new RuntimeException("Only the creator can start the lobby");
        }
        if (lobby.getStatus() != LobbyStatus.WAITING) {
            throw new RuntimeException("Lobby already started");
        }

        lobby.setStatus(LobbyStatus.RUNNING);
        lobby.setStartedAt(LocalDateTime.now());
        lobby.setCurrentTickIndex(0);
        lobbyRepository.save(lobby);
        return toResponse(lobby);
    }

    public List<LobbyResponse> getAllLobbies() {
        return lobbyRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }
}
