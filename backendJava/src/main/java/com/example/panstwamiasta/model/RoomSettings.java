package com.example.panstwamiasta.model;

import java.util.List;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class RoomSettings {
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> categories;

    private Integer timePerRound;
    private Integer rounds;
    private Integer maxPlayers;

    public RoomSettings() {}

    public RoomSettings(List<String> categories, Integer timePerRound, Integer rounds, Integer maxPlayers) {
        this.categories = categories;
        this.timePerRound = timePerRound;
        this.rounds = rounds;
        this.maxPlayers = maxPlayers;
    }

    // Getters and setters
    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public Integer getTimePerRound() {
        return timePerRound;
    }

    public void setTimePerRound(Integer timePerRound) {
        this.timePerRound = timePerRound;
    }

    public Integer getRounds() {
        return rounds;
    }

    public void setRounds(Integer rounds) {
        this.rounds = rounds;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
