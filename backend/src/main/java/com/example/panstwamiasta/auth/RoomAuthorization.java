package com.example.panstwamiasta.auth;

import com.example.panstwamiasta.repository.PlayerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("roomAuth")
public class RoomAuthorization {

    private final PlayerRepository playerRepository;

    public RoomAuthorization(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public boolean isMember(String code, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof PlayerPrincipal principal)) {
            return false;
        }
        return principal.roomCode().equals(code)
                && playerRepository.isPlayerInRoom(code, principal.playerId());
    }

    public boolean isHost(String code, Authentication auth) {
        if (!isMember(code, auth)) {
            return false;
        }
        PlayerPrincipal principal = (PlayerPrincipal) auth.getPrincipal();
        return playerRepository.isCurrentHost(code, principal.playerId());
    }
}
