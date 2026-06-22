package com.example.panstwamiasta.dto;

import java.util.UUID;

public class SubmitVoteRequest {
    private UUID targetPlayerId;
    private String category;
    private boolean isValid;

    public SubmitVoteRequest() {}

    public SubmitVoteRequest(UUID targetPlayerId, String category, boolean isValid) {
        this.targetPlayerId = targetPlayerId;
        this.category = category;
        this.isValid = isValid;
    }

    public UUID getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }
}
