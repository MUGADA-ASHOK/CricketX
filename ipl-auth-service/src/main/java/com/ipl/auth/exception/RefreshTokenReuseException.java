package com.ipl.auth.exception;

/**
 * Thrown when a refresh token that has already been rotated (revoked) is
 * presented again to /api/auth/refresh. This is treated as a possible
 * token-theft signal: the entire session chain for that user is revoked
 * (see RefreshTokenService#rotate) rather than silently issuing new tokens.
 */
public class RefreshTokenReuseException extends RuntimeException {
    public RefreshTokenReuseException(String message) {
        super(message);
    }
}
