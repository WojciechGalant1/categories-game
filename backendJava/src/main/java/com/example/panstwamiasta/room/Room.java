package com.example.panstwamiasta.room;

import com.example.panstwamiasta.model.GameState;
import com.example.panstwamiasta.model.Player;
import com.example.panstwamiasta.model.RoomSettings;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    private String code;

    private boolean isPublic;

    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "room_code")
    private List<Player> players;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Integer> scores; // playerId -> score

    @Embedded
    private RoomSettings settings;

    @Embedded
    private GameState game;

    public enum RoomStatus {
        lobby, playing, reviewing, finished
    }

    public Room() {}

    public Room(String code, boolean isPublic, RoomStatus status, List<Player> players,
                Map<String, Integer> scores, RoomSettings settings, GameState game) {
        this.code = code;
        this.isPublic = isPublic;
        this.status = status;
        this.players = players;
        this.scores = scores;
        this.settings = settings;
        this.game = game;
    }

    // Getters and setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }

    public RoomSettings getSettings() {
        return settings;
    }

    public void setSettings(RoomSettings settings) {
        this.settings = settings;
    }

    public GameState getGame() {
        return game;
    }

    public void setGame(GameState game) {
        this.game = game;
    }
}
