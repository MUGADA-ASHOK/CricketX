package com.ipl.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RSA JWT generation and validation.
 * Uses asymmetric RS256 signing (Private Key for Auth Service, Public Key
 * for Gateway and downstream services).
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    private String privateKey;
    private String publicKey;
    private long accessTokenExpiration = 900000; // 15 mins
    private long refreshTokenExpiration = 604800000; // 7 days
    private long accessTokenRenewalThreshold = 180000; // 3 mins
}