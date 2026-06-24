package com.example.panstwamiasta.exception;

public class GameAlreadyStartedException extends RuntimeException {

    public GameAlreadyStartedException() {
        super("Game already started");
    }

    public GameAlreadyStartedException(String message) {
        super(message);
    }
}
