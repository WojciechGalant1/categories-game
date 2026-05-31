package com.example.panstwamiasta.dto;

import java.util.Map;
import java.util.UUID;

public class SubmitAnswersRequest {
    private UUID playerId;
    private Map<String, String> answers; // category -> answer

    public SubmitAnswersRequest() {}

    public SubmitAnswersRequest(UUID playerId, Map<String, String> answers) {
        this.playerId = playerId;
        this.answers = answers;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }
}
