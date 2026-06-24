package com.example.panstwamiasta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class JoinRoomRequest {
    @NotBlank(message = "Nick is required")
    @Size(max = 30)
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
