package com.ipl.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import com.ipl.gateway.security.GatewayJwtProperties;
import com.ipl.gateway.security.RsaKeyUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;

/**
 * Global filter that parses valid JWTs and injects standard identity headers
 * (X-User-Id, X-User-Email, X-User-Role) onto the downstream request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserContextGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayJwtProperties jwtProperties;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.publicKey = RsaKeyUtils.parsePublicKey(jwtProperties.getPublicKey());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId != null ? userId : "")
                        .header("X-User-Email", email != null ? email : "")
                        .header("X-User-Role", role != null ? role : "")
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (Exception ex) {
                // If invalid, the Spring Security filter handles rejection
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
