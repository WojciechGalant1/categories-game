package com.example.panstwamiasta.model;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "players", indexes = @Index(name = "idx_players_room_code", columnList = "room_code"))
public class Player {
    @Id
    private UUID id;

    private String nick;
    private boolean isHost;

    @Column(name = "room_code", length = 10)
    private String roomCode;

    public Player() {}

    public Player(UUID id, String nick, boolean isHost) {
        this.id = id;
        this.nick = nick;
        this.isHost = isHost;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    @JsonProperty("isHost")
    public boolean isHost() {
        return isHost;
    }

    public void setIsHost(boolean isHost) {
        this.isHost = isHost;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }
}
