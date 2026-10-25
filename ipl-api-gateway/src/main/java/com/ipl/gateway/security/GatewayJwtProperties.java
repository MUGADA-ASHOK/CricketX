package com.ipl.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds jwt.public-key (JWT_PUBLIC_KEY env var). Gateway only requires
 * the RSA Public Key to verify tokens issued by ipl-auth-service.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class GatewayJwtProperties {

    private String publicKey;
}
