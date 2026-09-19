package com.ipl.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles both:
 * - no token at all on a protected route
 * - a token that was present but failed validation
 *
 * Both cases return the same 401 JSON shape; the request is never
 * forwarded to a downstream service in either case.
 */
@Component
@RequiredArgsConstructor
public class GatewayAuthenticationEntryPoint
        implements ServerAuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException ex) {

        return GatewayErrorResponseWriter.write(
                exchange,
                jsonMapper,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required"
        );
    }
}