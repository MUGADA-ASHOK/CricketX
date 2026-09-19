package com.ipl.gateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Minimal unauthenticated Authentication holding just the raw bearer
 * token string, pending validation by JwtAuthenticationManager.
 *
 * A hand-rolled class instead of pulling in
 * org.springframework.security.oauth2.server.resource's
 * BearerTokenAuthenticationToken, to avoid adding the OAuth2 Resource
 * Server dependency for a single class when this Gateway deliberately
 * doesn't use that module's JWT decoding (see SecurityConfig for why).
 */
public class RawJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;

    public RawJwtAuthenticationToken(String token) {
        // Explicit cast needed: this Spring Security version added a second
        // AbstractAuthenticationToken constructor overload (taking a builder
        // type), making a bare super(null) ambiguous between the two.
        super((Collection<? extends GrantedAuthority>) null);
        this.token = token;
        setAuthenticated(false);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }
}