package com.example.panstwamiasta.dto;

public class PublicRoom {
    private String code;
    private String hostNick;
    private int playersCount;
    private int maxPlayers;

    public PublicRoom() {}

    public PublicRoom(String code, String hostNick, int playersCount, int maxPlayers) {
        this.code = code;
        this.hostNick = hostNick;
        this.playersCount = playersCount;
        this.maxPlayers = maxPlayers;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getHostNick() {
        return hostNick;
    }

    public void setHostNick(String hostNick) {
        this.hostNick = hostNick;
    }

    public int getPlayersCount() {
        return playersCount;
    }

    public void setPlayersCount(int playersCount) {
        this.playersCount = playersCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
