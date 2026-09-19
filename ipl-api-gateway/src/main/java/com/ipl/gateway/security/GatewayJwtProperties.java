package com.ipl.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds jwt.secret (JWT_SECRET env var). This MUST be the exact same
 * value configured on ipl-auth-service - the Gateway validates the same
 * HMAC-signed access tokens Auth Service issues, it does not have (or
 * need) its own independent JWT configuration.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class GatewayJwtProperties {

    private String secret;
}
