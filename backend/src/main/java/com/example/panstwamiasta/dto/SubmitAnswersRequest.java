package com.example.panstwamiasta.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class SubmitAnswersRequest {
    @NotNull
    private Map<String, String> answers;

    public SubmitAnswersRequest() {}

    public SubmitAnswersRequest(Map<String, String> answers) {
        this.answers = answers;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }
}
