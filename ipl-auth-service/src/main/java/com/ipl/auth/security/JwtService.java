package com.ipl.auth.security;

import com.ipl.auth.config.JwtProperties;
import com.ipl.auth.enums.Role;
import com.ipl.auth.exception.InvalidTokenException;
import com.ipl.auth.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Single source of truth for JWT creation, parsing and validation.
 *
 * Uses asymmetric RS256 signing (RSA 2048-bit keypair). Auth Service signs
 * tokens with its Private Key; API Gateway and downstream services only need
 * the Public Key to verify tokens, preventing any downstream service from
 * forging authentication credentials.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private final JwtProperties jwtProperties;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void initKeys() {
        this.privateKey = RsaKeyUtils.parsePrivateKey(jwtProperties.getPrivateKey());
        this.publicKey = RsaKeyUtils.parsePublicKey(jwtProperties.getPublicKey());
        log.info("Initialized RSA RS256 keys for Auth Service");
    }

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
     * token's "type" claim matches {@code expectedType}.
     */
    public Claims parseAndValidate(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
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

    public PublicKey getPublicKey() {
        return publicKey;
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
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
