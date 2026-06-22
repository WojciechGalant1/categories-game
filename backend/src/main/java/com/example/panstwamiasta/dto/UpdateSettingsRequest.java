package com.example.panstwamiasta.dto;

import com.example.panstwamiasta.model.RoomSettings;

public class UpdateSettingsRequest {
    private RoomSettings settings;

    public UpdateSettingsRequest() {}

    public UpdateSettingsRequest(RoomSettings settings) {
        this.settings = settings;
    }

    public RoomSettings getSettings() {
        return settings;
    }

    public void setSettings(RoomSettings settings) {
        this.settings = settings;
    }
}
