package com.ipl.order.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final JwtValidationProperties jwtProperties;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.publicKey = RsaKeyUtils.parsePublicKey(jwtProperties.getPublicKey());
        log.info("Initialized RSA RS256 public key verifier for Order Service");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                authenticate(token);
            } catch (RuntimeException ex) {
                log.debug("JWT authentication skipped: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid or expired access token", ex);
        }

        String type = claims.get("type", String.class);
        if (!TOKEN_TYPE_ACCESS.equals(type)) {
            throw new IllegalArgumentException("Token is not an access token");
        }

        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Token is missing the role claim");
        }

        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
