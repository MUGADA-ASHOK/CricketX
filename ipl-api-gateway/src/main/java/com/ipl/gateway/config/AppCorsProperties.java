package com.ipl.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds app.cors.allowed-origins (comma-separated). No frontend exists yet
 * for this project, so this defaults to a typical local dev origin -
 * update it once a real frontend origin is known. Never defaults to "*".
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class AppCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
}
