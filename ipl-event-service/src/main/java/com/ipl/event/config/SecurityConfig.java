package com.ipl.event.config;

import com.ipl.event.dto.response.ErrorResponse;
import com.ipl.event.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * RBAC model for Event Service, enforced at two levels (defense in depth,
 * matches ipl-auth-service's own Gateway-vs-service split):
 *
 * 1. COARSE, here: URL + HTTP-method role rules below decide who can even
 *    reach a controller method.
 *
 * 2. FINE-GRAINED, in the controllers (@PreAuthorize, matching the coarse
 *    rule for defense in depth) and in the services (ownership checks).
 *
 * Route table:
 *   POST   /api/events/matches                         ADMIN only
 *   POST   /api/events/matches/{id}/approve           ADMIN only
 *   GET    /api/events/matches, /matches/{id}         any authenticated role
 *   POST   /api/events/matches/{id}/configuration    FRANCHISE only
 *   PUT    /api/events/matches/{id}/configuration    FRANCHISE only
 *   GET    /api/events/matches/{id}/configuration    any authenticated role
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Explicitly registers ObjectMapper as a Spring bean.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return this.objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // BCCI/Admin creates a match
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/matches"
                        ).hasRole("ADMIN")

                        // BCCI/Admin approves a configured match
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/matches/*/approve"
                        ).hasRole("ADMIN")

                        // Home franchise or Admin configures a match
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/matches/*/configuration"
                        ).hasAnyRole("FRANCHISE", "ADMIN")

                        // Home franchise or Admin updates configuration
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/events/matches/*/configuration"
                        ).hasAnyRole("FRANCHISE", "ADMIN")

                        // BCCI/Admin creates Stadiums and Franchises
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events/stadiums/**",
                                "/api/events/franchises/**"
                        ).hasRole("ADMIN")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(this::handleUnauthorized)
                        .accessDeniedHandler(this::handleForbidden)
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Handles requests where the user is not authenticated.
     */
    private void handleUnauthorized(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.core.AuthenticationException ex
    ) throws IOException {

        writeError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Authentication required",
                "UNAUTHORIZED",
                request.getRequestURI()
        );
    }

    /**
     * Handles requests where the user is authenticated
     * but does not have sufficient privileges.
     */
    private void handleForbidden(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException ex
    ) throws IOException {

        writeError(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "Access denied",
                "ACCESS_DENIED",
                request.getRequestURI()
        );
    }

    /**
     * Creates the standard ErrorResponse JSON.
     */
    private void writeError(
            HttpServletResponse response,
            int status,
            String message,
            String errorCode,
            String path
    ) throws IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ErrorResponse.of(
                                message,
                                errorCode,
                                path
                        )
                )
        );
    }
}