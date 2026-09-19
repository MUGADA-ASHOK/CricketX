package com.ipl.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fires when the token is valid and the user is authenticated, but their
 * role doesn't satisfy the route's authorization rule (e.g. USER calling
 * POST /events). The request is never forwarded to the downstream service.
 */
@Component
@RequiredArgsConstructor
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException denied) {

        return GatewayErrorResponseWriter.write(
                exchange,
                jsonMapper,
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "You do not have permission to access this resource"
        );
    }
}