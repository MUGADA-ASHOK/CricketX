package com.ipl.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds services.auth-url / services.event-url / services.order-url.
 * Static URLs for now - no Eureka/Consul/service discovery at this stage,
 * per the current project scope.
 */
@Component
@ConfigurationProperties(prefix = "services")
@Getter
@Setter
public class ServiceUrlProperties {

    private String authUrl;
    private String eventUrl;
    private String orderUrl;
}
