package com.example.panstwamiasta.model;

import java.util.List;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class RoomSettings {
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Size(min = 1)
    private List<String> categories;

    @Min(1)
    @Max(10)
    private Integer timePerRound;

    @Min(1)
    @Max(20)
    private Integer rounds;

    @Min(2)
    @Max(16)
    private Integer maxPlayers;

    public RoomSettings() {}

    public RoomSettings(List<String> categories, Integer timePerRound, Integer rounds, Integer maxPlayers) {
        this.categories = categories;
        this.timePerRound = timePerRound;
        this.rounds = rounds;
        this.maxPlayers = maxPlayers;
    }

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
