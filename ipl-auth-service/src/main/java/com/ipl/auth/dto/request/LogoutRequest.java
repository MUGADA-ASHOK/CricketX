package com.ipl.auth.dto.request;

/**
 * Optional body for POST /api/auth/logout.
 *
 * The access token is taken from the Authorization header (and is what
 * actually gets blocklisted). The refresh token here is optional and, if
 * provided, lets us also revoke the corresponding session/row so the
 * refresh token can't be used again after logout.
 */
public record LogoutRequest(
        String refreshToken
) {
}
