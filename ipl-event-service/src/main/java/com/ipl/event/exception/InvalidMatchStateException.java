package com.ipl.event.exception;

/**
 * Thrown when an operation is attempted while the match is in the wrong
 * status for it - e.g. approving a match still in CONFIGURATION_PENDING,
 * or configuring a match that's already WAITING_FOR_APPROVAL or later.
 */
public class InvalidMatchStateException extends RuntimeException {
    public InvalidMatchStateException(String message) {
        super(message);
    }
}
