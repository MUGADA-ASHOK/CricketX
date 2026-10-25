package com.ipl.order.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds jwt.public-key (JWT_PUBLIC_KEY env var) - Order Service only needs
 * the RSA Public Key to verify tokens.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtValidationProperties {

    private String publicKey;
}
