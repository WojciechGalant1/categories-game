package com.example.panstwamiasta.auth;

public class InvalidPlayerTokenException extends RuntimeException {

    public InvalidPlayerTokenException(String message) {
        super(message);
    }
}
