package com.ipl.event.exception;

/**
 * Thrown when an authenticated FRANCHISE user attempts to act on a match
 * they are not the home franchise for. Never trust a client-supplied
 * franchise/team ID for this check - always compare against the franchise
 * resolved from the authenticated user's own identity.
 */
public class UnauthorizedFranchiseException extends RuntimeException {
    public UnauthorizedFranchiseException(String message) {
        super(message);
    }
}
