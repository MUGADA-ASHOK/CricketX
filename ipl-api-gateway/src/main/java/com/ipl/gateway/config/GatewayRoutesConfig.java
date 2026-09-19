package com.ipl.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Static routing configuration - no service discovery at this stage.
 *
 * Auth Service route rewrites the public /auth/** prefix to the real
 * controller base path /api/auth/** before forwarding. This keeps the
 * public API exactly as specified (/auth/**) without requiring any change
 * to the already-implemented Auth Service, whose actual controllers live
 * under /api/auth/**.
 *
 * Event/Order routes forward the path unchanged, since neither service is
 * implemented yet - their controllers should be built to match /events/**
 * and /orders/** directly.
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
                        .uri(serviceUrls.getEventUrl()))
                .route("ipl-order-service", r -> r.path("/orders/**")
                        .uri(serviceUrls.getOrderUrl()))
                .build();
    }
}
