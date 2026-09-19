package com.ipl.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional BCCI/ADMIN bootstrap credentials, read from bcci.username /
 * bcci.email / bcci.password (BCCI_USERNAME / BCCI_EMAIL / BCCI_PASSWORD
 * env vars, or -Dspring-boot.run.arguments). Never hardcoded in source,
 * never logged.
 *
 * If any of the three is blank, AdminBootstrapRunner skips bootstrap
 * entirely - the service starts normally with no ADMIN account created.
 */
@Component
@ConfigurationProperties(prefix = "bcci")
@Getter
@Setter
public class BcciProperties {

    private String username;
    private String email;
    private String password;

    public boolean isConfigured() {
        return hasText(username) && hasText(email) && hasText(password);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
