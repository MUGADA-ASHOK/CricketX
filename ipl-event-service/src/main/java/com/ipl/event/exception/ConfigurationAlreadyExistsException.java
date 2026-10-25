package com.ipl.event.exception;

public class ConfigurationAlreadyExistsException extends RuntimeException {
    public ConfigurationAlreadyExistsException(String message) {
        super(message);
    }
}
