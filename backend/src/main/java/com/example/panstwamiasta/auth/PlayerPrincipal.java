package com.example.panstwamiasta.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PlayerPrincipal implements Authentication {

    private final UUID playerId;
    private final String roomCode;
    private final String nick;
    private final boolean hostHint;

    public PlayerPrincipal(UUID playerId, String roomCode, String nick, boolean hostHint) {
        this.playerId = playerId;
        this.roomCode = roomCode;
        this.nick = nick;
        this.hostHint = hostHint;
    }

    public UUID playerId() {
        return playerId;
    }

    public String roomCode() {
        return roomCode;
    }

    public String nick() {
        return nick;
    }

    public boolean hostHint() {
        return hostHint;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_PLAYER"));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        throw new IllegalArgumentException("Cannot change authenticated flag");
    }

    @Override
    public String getName() {
        return playerId.toString();
    }
}
