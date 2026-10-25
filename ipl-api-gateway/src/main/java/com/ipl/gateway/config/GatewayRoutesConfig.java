package com.ipl.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Static routing configuration:
 * Rewrites public prefixes (/auth/**, /events/**, /orders/**) to the real
 * controller base paths (/api/auth/**, /api/events/**, /api/orders/**)
 * before forwarding downstream.
 */
@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {

    private final ServiceUrlProperties serviceUrls;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("ipl-auth-service", r -> r.path("/auth/**")
                        .filters(f -> f.rewritePath("/auth/(?<segment>.*)", "/api/auth/${segment}"))
                        .uri(serviceUrls.getAuthUrl()))
                .route("ipl-event-service", r -> r.path("/events/**")
                        .filters(f -> f.rewritePath("/events/(?<segment>.*)", "/api/events/${segment}"))
                        .uri(serviceUrls.getEventUrl()))
                .route("ipl-order-service", r -> r.path("/orders/**")
                        .filters(f -> f.rewritePath("/orders/(?<segment>.*)", "/api/orders/${segment}"))
                        .uri(serviceUrls.getOrderUrl()))
                .build();
    }
}
