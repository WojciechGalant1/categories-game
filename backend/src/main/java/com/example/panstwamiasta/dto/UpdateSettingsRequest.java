package com.example.panstwamiasta.dto;

import com.example.panstwamiasta.model.RoomSettings;

import java.util.UUID;

public class UpdateSettingsRequest {
    private UUID playerId;
    private RoomSettings settings;

    public UpdateSettingsRequest() {}

    public UpdateSettingsRequest(UUID playerId, RoomSettings settings) {
        this.playerId = playerId;
        this.settings = settings;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public RoomSettings getSettings() {
        return settings;
    }

    public void setSettings(RoomSettings settings) {
        this.settings = settings;
    }
}
