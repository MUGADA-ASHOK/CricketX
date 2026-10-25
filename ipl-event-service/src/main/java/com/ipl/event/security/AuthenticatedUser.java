package com.ipl.event.security;

/**
 * The authenticated caller's identity, extracted from the validated access
 * token's claims (sub=userId, email, role). Set as the Authentication's
 * principal by JwtAuthenticationFilter, and used by services that need to
 * check fine-grained ownership - e.g. "does THIS franchise own THIS match" -
 * which is exactly the kind of check that must never be trusted from a
 * request body/param (see MatchConfigurationService).
 */
public record AuthenticatedUser(Long userId, String email, String role) {
}
