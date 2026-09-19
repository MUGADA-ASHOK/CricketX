package com.ipl.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logs method, path, and resulting status/duration for every request.
 * Deliberately never logs the Authorization header, tokens, or request
 * bodies (which could contain passwords).
 */
@Component
@Slf4j
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        log.info("Incoming request: {} {}", request.getMethod(), request.getPath());

        return chain.filter(exchange).doFinally(signalType -> {
            long durationMs = System.currentTimeMillis() - start;
            log.info("Completed {} {} -> status={} ({} ms)",
                    request.getMethod(), request.getPath(),
                    exchange.getResponse().getStatusCode(), durationMs);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
