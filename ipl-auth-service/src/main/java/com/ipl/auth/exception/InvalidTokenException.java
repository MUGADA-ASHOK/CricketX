package com.ipl.auth.exception;

/**
 * Thrown for a JWT that is malformed, has a bad signature, has the wrong
 * token type for the endpoint (e.g. a refresh token used as an access
 * token), or a refresh token whose hash doesn't match what's stored.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
