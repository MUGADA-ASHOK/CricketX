package com.ipl.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates access tokens issued by ipl-auth-service.
 *
 * Mirrors JwtService#parseAndValidate in ipl-auth-service (same jjwt
 * library and version, same claim names: "type", "role", "email") rather
 * than using Spring Security's OAuth2 Resource Server JWT decoder - see
 * SecurityConfig for why. Signature, structure and expiration are all
 * verified by jjwt's parser; a mismatched signature or an expired token
 * both surface as a JwtException here, which is translated into a
 * BadCredentialsException so it flows through Spring Security's normal
 * authentication-failure handling (-> 401 via GatewayAuthenticationEntryPoint).
 *
 * A refresh token (type=refresh) is explicitly rejected here - only an
 * access token can authenticate a request to Event/Order services, exactly
 * as ipl-auth-service's own JwtAuthenticationFilter enforces internally.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final GatewayJwtProperties jwtProperties;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> validate(token))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Authentication validate(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired access token", ex);
        }

        String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(type)) {
            throw new BadCredentialsException("Token is not an access token");
        }

        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null || role.isBlank()) {
            throw new BadCredentialsException("Token is missing the role claim");
        }

        String email = claims.get(CLAIM_EMAIL, String.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(email, null, authorities);
        authenticated.setAuthenticated(true);
        return authenticated;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
