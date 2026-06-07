package com.example.panstwamiasta.model;

import java.util.List;
import java.util.Map;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class GameState {
    private int currentRound;
    private String currentLetter;
    private Long roundStartedAt;
    private Long roundEndsAt; // Unix ms when the round auto-stops; source of truth for the timer

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Map<String, String>> answers; // playerId -> category -> answer

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Map<String, Map<String, Boolean>>> votes; // targetPlayerId -> category -> voterId -> isValid

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> stoppedPlayers; // player UUIDs

    @Transient
    private int timeLeft; // always 0, computed on read

    @Transient
    private int mainTimeLeft; // seconds left, computed on read from roundEndsAt

    public GameState() {}

    public GameState(int currentRound, String currentLetter, Long roundStartedAt, Long roundEndsAt,
                     Map<String, Map<String, String>> answers,
                     Map<String, Map<String, Map<String, Boolean>>> votes,
                     List<String> stoppedPlayers, int timeLeft, int mainTimeLeft) {
        this.currentRound = currentRound;
        this.currentLetter = currentLetter;
        this.roundStartedAt = roundStartedAt;
        this.roundEndsAt = roundEndsAt;
        this.answers = answers;
        this.votes = votes;
        this.stoppedPlayers = stoppedPlayers;
        this.timeLeft = timeLeft;
        this.mainTimeLeft = mainTimeLeft;
    }

    // Getters and setters
    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public String getCurrentLetter() {
        return currentLetter;
    }

    public void setCurrentLetter(String currentLetter) {
        this.currentLetter = currentLetter;
    }

    public Long getRoundStartedAt() {
        return roundStartedAt;
    }

    public void setRoundStartedAt(Long roundStartedAt) {
        this.roundStartedAt = roundStartedAt;
    }

    public Long getRoundEndsAt() {
        return roundEndsAt;
    }

    public void setRoundEndsAt(Long roundEndsAt) {
        this.roundEndsAt = roundEndsAt;
    }

    public Map<String, Map<String, String>> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, Map<String, String>> answers) {
        this.answers = answers;
    }

    public Map<String, Map<String, Map<String, Boolean>>> getVotes() {
        return votes;
    }

    public void setVotes(Map<String, Map<String, Map<String, Boolean>>> votes) {
        this.votes = votes;
    }

    public List<String> getStoppedPlayers() {
        return stoppedPlayers;
    }

    public void setStoppedPlayers(List<String> stoppedPlayers) {
        this.stoppedPlayers = stoppedPlayers;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
    }

    public int getMainTimeLeft() {
        return mainTimeLeft;
    }

    public void setMainTimeLeft(int mainTimeLeft) {
        this.mainTimeLeft = mainTimeLeft;
    }
}
