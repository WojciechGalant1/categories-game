package com.example.panstwamiasta.dto;

import java.util.UUID;

public class PlayerIdRequest {
    private UUID playerId;

    public PlayerIdRequest() {}

    public PlayerIdRequest(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
}
