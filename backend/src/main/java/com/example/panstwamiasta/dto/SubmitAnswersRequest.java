package com.example.panstwamiasta.dto;

import java.util.Map;

public class SubmitAnswersRequest {
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
