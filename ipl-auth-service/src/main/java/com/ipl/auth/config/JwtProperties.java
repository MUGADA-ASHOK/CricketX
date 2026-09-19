package com.ipl.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds jwt.secret / jwt.access-token-expiration / jwt.refresh-token-expiration
 * from application.properties (which in turn read JWT_SECRET,
 * JWT_ACCESS_EXPIRATION, JWT_REFRESH_EXPIRATION env vars). No expiration
 * value or secret is ever hardcoded in Java code.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;

    /**
     * If a request's access token has less than this many milliseconds left
     * before expiry, JwtAuthenticationFilter silently issues a fresh one in
     * the X-New-Access-Token response header instead of letting the user
     * get logged out mid-session. Should be meaningfully smaller than
     * accessTokenExpiration (e.g. ~20% of it).
     */
    private long accessTokenRenewalThreshold;
}