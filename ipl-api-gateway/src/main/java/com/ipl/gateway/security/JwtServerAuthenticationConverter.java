package com.ipl.gateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Reads "Authorization: Bearer <token>" from the request. If absent or
 * malformed, returns an empty Mono - the request simply proceeds
 * unauthenticated (public routes still work; protected routes are then
 * rejected by the authorizeExchange rules with a 401 via
 * GatewayAuthenticationEntryPoint).
 */
@Component
public class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Mono.empty();
        }

        String token = header.substring(BEARER_PREFIX.length());
        return Mono.just(new RawJwtAuthenticationToken(token));
    }
}
