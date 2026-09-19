package com.ipl.auth.service;

import com.ipl.auth.config.JwtProperties;
import com.ipl.auth.entity.RefreshToken;
import com.ipl.auth.entity.User;
import com.ipl.auth.exception.InvalidTokenException;
import com.ipl.auth.exception.RefreshTokenReuseException;
import com.ipl.auth.exception.TokenExpiredException;
import com.ipl.auth.repository.RefreshTokenRepository;
import com.ipl.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Owns the full lifecycle of refresh tokens: issuing, persisting (as a
 * hash, never the raw token), rotating on every /refresh call, and
 * detecting reuse of an already-rotated token.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public record TokenPair(String accessToken, String refreshToken, User user) {
    }

    /**
     * Issues a brand-new access + refresh token pair for a freshly
     * authenticated user (login).
     */
    @Transactional
    public TokenPair issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = issueRefreshToken(user);
        return new TokenPair(accessToken, refreshToken, user);
    }

    /**
     * Rotates a refresh token: validates it, revokes the old one, and
     * issues a brand-new access + refresh token pair.
     *
     * If the presented token was already revoked (i.e. it was already used
     * once before, or the session was logged out), this is treated as
     * refresh-token reuse: the entire remaining session chain for that user
     * is revoked and a RefreshTokenReuseException is thrown instead of
     * silently issuing new tokens.
     */
    @Transactional
    public TokenPair rotate(String rawRefreshToken) {
        Claims claims = jwtService.parseAndValidate(rawRefreshToken, JwtService.TOKEN_TYPE_REFRESH);
        String jti = jwtService.extractJti(claims);

        RefreshToken record = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognized"));

        if (!record.getTokenHash().equals(hash(rawRefreshToken))) {
            throw new InvalidTokenException("Refresh token does not match the stored session");
        }

        if (record.isRevoked()) {
            revokeAllForUser(record.getUser());
            throw new RefreshTokenReuseException(
                    "This refresh token was already used or revoked. All sessions for this account have been revoked as a precaution.");
        }

        if (record.isExpired()) {
            throw new TokenExpiredException("Refresh token has expired");
        }

        User user = record.getUser();
        String newJti = UUID.randomUUID().toString();

        record.setRevokedAt(Instant.now());
        record.setReplacedByJti(newJti);
        refreshTokenRepository.save(record);

        String newRawRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getRole(), newJti);
        RefreshToken newRecord = RefreshToken.builder()
                .user(user)
                .jti(newJti)
                .tokenHash(hash(newRawRefreshToken))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(newRecord);

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenPair(newAccessToken, newRawRefreshToken, user);
    }

    /**
     * Revokes the session behind a refresh token, e.g. on logout. Silently
     * no-ops if the token is already invalid/expired - logout should still
     * succeed in that case.
     */
    @Transactional
    public void revokeSession(String rawRefreshToken) {
        try {
            Claims claims = jwtService.parseAndValidate(rawRefreshToken, JwtService.TOKEN_TYPE_REFRESH);
            refreshTokenRepository.findByJti(jwtService.extractJti(claims))
                    .ifPresent(rt -> {
                        rt.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(rt);
                    });
        } catch (RuntimeException ex) {
            // Already invalid/expired - nothing meaningful left to revoke.
        }
    }

    private String issueRefreshToken(User user) {
        String jti = UUID.randomUUID().toString();
        String rawToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), user.getRole(), jti);

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .jti(jti)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    private void revokeAllForUser(User user) {
        Instant now = Instant.now();
        List<RefreshToken> active = refreshTokenRepository.findByUserAndRevokedAtIsNull(user);
        active.forEach(rt -> rt.setRevokedAt(now));
        refreshTokenRepository.saveAll(active);
    }

    /**
     * SHA-256 of the raw refresh token (not BCrypt). This is intentional:
     * refresh tokens are already high-entropy random JWTs, not user-chosen
     * low-entropy passwords, so there's no brute-force risk to mitigate with
     * a slow hash - and BCrypt's 72-byte input limit would silently
     * truncate a JWT of this length anyway.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
