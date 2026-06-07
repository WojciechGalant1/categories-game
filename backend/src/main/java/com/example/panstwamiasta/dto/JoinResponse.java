package com.example.panstwamiasta.dto;

import java.util.UUID;

public class JoinResponse {
    private String code;
    private UUID playerId;

    public JoinResponse() {}

    public JoinResponse(String code, UUID playerId) {
        this.code = code;
        this.playerId = playerId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
}
