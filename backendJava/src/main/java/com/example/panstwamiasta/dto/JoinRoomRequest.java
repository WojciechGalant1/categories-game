package com.example.panstwamiasta.dto;

public class JoinRoomRequest {
    private String nick;

    public JoinRoomRequest() {}

    public JoinRoomRequest(String nick) {
        this.nick = nick;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }
}
