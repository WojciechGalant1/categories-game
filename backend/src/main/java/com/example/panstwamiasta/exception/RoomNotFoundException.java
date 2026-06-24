package com.example.panstwamiasta.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException() {
        super("Room not found");
    }

    public RoomNotFoundException(String message) {
        super(message);
    }
}
