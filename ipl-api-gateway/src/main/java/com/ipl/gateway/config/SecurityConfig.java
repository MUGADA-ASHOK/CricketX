package com.ipl.gateway.config;

import com.ipl.gateway.security.GatewayAccessDeniedHandler;
import com.ipl.gateway.security.GatewayAuthenticationEntryPoint;
import com.ipl.gateway.security.JwtAuthenticationManager;
import com.ipl.gateway.security.JwtServerAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Reactive Spring Security configuration - the Gateway's security
 * boundary. Stateless (no server-side sessions, no security context
 * persisted between requests).
 *
 * Deliberately does NOT use oauth2ResourceServer().jwt(): that requires
 * pinning one exact HMAC algorithm up front, which doesn't fit
 * ipl-auth-service's JwtService (jjwt auto-selects the HMAC variant from
 * the secret's byte length). JwtAuthenticationManager instead validates
 * tokens the same way JwtService does, wired in via a standard
 * AuthenticationWebFilter - still proper reactive Spring Security, just
 * not the OAuth2 Resource Server abstraction specifically.
 *
 * Authorization here is deliberately coarse-grained (role-based route
 * rules only). Fine-grained business authorization (e.g. "does this
 * FRANCHISE actually own this match?") belongs inside Event/Order Service.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationManager jwtAuthenticationManager;
    private final JwtServerAuthenticationConverter jwtServerAuthenticationConverter;
    private final GatewayAuthenticationEntryPoint authenticationEntryPoint;
    private final GatewayAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(jwtAuthenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(jwtServerAuthenticationConverter);
        // A token that IS present but fails validation (bad signature, expired,
        // wrong type) must also produce our 401 JSON shape, not Spring's default.
        authenticationWebFilter.setAuthenticationFailureHandler(
                new ServerAuthenticationEntryPointFailureHandler(authenticationEntryPoint));

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        // Public Auth Service endpoints. Paths here are the PUBLIC
                        // /auth/** prefix - GatewayRoutesConfig rewrites this to the
                        // real /api/auth/** controller path only when forwarding.
                        .pathMatchers(HttpMethod.POST, "/auth/login", "/auth/register", "/auth/refresh").permitAll()

                        // Event Service - coarse-grained role rules (fine-grained
                        // authorization belongs inside Event Service itself)
                        .pathMatchers(HttpMethod.GET, "/events/**").hasAnyRole("USER", "FRANCHISE", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/events/**").hasAnyRole("FRANCHISE", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/events/**").hasAnyRole("FRANCHISE", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/events/**").hasAnyRole("FRANCHISE", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/events/**").hasRole("ADMIN")

                        // Order Service - just requires authentication; role-specific
                        // rules (USER manages own orders, ADMIN administrative ops)
                        // belong inside Order Service.
                        .pathMatchers("/orders/**").authenticated()

                        // Everything else - e.g. /auth/logout, /auth/me - requires auth
                        .anyExchange().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION);

        return http.build();
    }
}
