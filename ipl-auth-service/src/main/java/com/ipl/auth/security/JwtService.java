package com.ipl.auth.security;

import com.ipl.auth.config.JwtProperties;
import com.ipl.auth.enums.Role;
import com.ipl.auth.exception.InvalidTokenException;
import com.ipl.auth.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Single source of truth for JWT creation, parsing and validation.
 * No other class should build or parse a JWT directly.
 *
 * Uses HMAC-SHA signing with a secret loaded from configuration
 * (jwt.secret -> JWT_SECRET env var) - never hardcoded.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(Long userId, String email, Role role) {
        return buildToken(userId, email, role, TOKEN_TYPE_ACCESS,
                jwtProperties.getAccessTokenExpiration(), UUID.randomUUID().toString());
    }

    public String generateRefreshToken(Long userId, String email, Role role, String jti) {
        return buildToken(userId, email, role, TOKEN_TYPE_REFRESH,
                jwtProperties.getRefreshTokenExpiration(), jti);
    }

    /**
     * Parses the token, verifies signature and expiration, and confirms the
     * token's "type" claim matches {@code expectedType}. Callers must never
     * skip this type check - it's what stops a refresh token being used to
     * authenticate a normal API request, and vice versa.
     */
    public Claims parseAndValidate(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException("Token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid token");
        }

        String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new InvalidTokenException(
                    "Expected a " + expectedType + " token but received a " + actualType + " token");
        }
        return claims;
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String extractEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    private String buildToken(Long userId, String email, Role role, String type, long expirationMillis, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
