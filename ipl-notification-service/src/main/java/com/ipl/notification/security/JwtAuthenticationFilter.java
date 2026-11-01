package com.ipl.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidationProperties properties;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.publicKey = RsaKeyUtils.parsePublicKey(properties.getPublicKey());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Check Gateway injected identity headers first
        String headerUserId = request.getHeader("X-User-Id");
        String headerRole = request.getHeader("X-User-Role");
        String headerEmail = request.getHeader("X-User-Email");

        if (headerUserId != null && !headerUserId.isBlank() && headerRole != null && !headerRole.isBlank()) {
            try {
                Long userId = Long.parseLong(headerUserId);
                String normalizedRole = headerRole.startsWith("ROLE_") ? headerRole : "ROLE_" + headerRole;
                AuthenticatedUser principal = new AuthenticatedUser(userId, headerEmail, normalizedRole);
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(normalizedRole));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            } catch (NumberFormatException ignored) {
            }
        }

        // Direct token parsing fallback
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                Long userId = Long.parseLong(claims.getSubject());
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                String normalizedRole = role != null && role.startsWith("ROLE_") ? role : "ROLE_" + role;
                AuthenticatedUser principal = new AuthenticatedUser(userId, email, normalizedRole);
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(normalizedRole));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                log.warn("Invalid JWT in Notification Service: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}

