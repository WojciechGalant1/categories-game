package com.example.panstwamiasta.dto;

public class CreateRoomRequest {
    private String nick;
    private Boolean isPublic;

    public CreateRoomRequest() {}

    public CreateRoomRequest(String nick, Boolean isPublic) {
        this.nick = nick;
        this.isPublic = isPublic;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
