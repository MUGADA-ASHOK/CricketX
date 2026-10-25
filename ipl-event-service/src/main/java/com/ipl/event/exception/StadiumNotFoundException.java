package com.ipl.event.exception;

public class StadiumNotFoundException extends RuntimeException {
    public StadiumNotFoundException(String message) {
        super(message);
    }
}
