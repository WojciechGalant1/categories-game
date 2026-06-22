package com.example.panstwamiasta.dto;

import java.util.UUID;

public class JoinResponse {
    private String code;
    private UUID playerId;
    private String accessToken;

    public JoinResponse() {}

    public JoinResponse(String code, UUID playerId, String accessToken) {
        this.code = code;
        this.playerId = playerId;
        this.accessToken = accessToken;
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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
