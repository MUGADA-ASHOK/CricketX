package com.ipl.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Server-side record of an issued refresh token / session.
 *
 * The raw refresh token is NEVER stored here - only a hash of it
 * (see RefreshTokenService), so a database leak alone cannot be used to
 * forge a session. Rotation and reuse-detection are implemented against
 * this table: {@link #jti} identifies the token, {@link #revokedAt} and
 * {@link #replacedByJti} form the rotation chain.
 */
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refresh_tokens_jti", columnNames = "jti")
        },
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Unique token identifier embedded as the JWT "jti" claim.
     */
    @Column(nullable = false, length = 100)
    private String jti;

    /**
     * Hash (not the raw token) of the refresh token, used to verify a
     * presented token actually matches this record.
     */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * JTI of the token that replaced this one during rotation, if any.
     * Lets us walk the rotation chain if reuse is detected.
     */
    @Column(name = "replaced_by_jti", length = 100)
    private String replacedByJti;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
