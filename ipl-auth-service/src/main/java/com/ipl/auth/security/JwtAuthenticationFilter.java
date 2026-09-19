package com.ipl.auth.security;

import com.ipl.auth.config.JwtProperties;
import com.ipl.auth.enums.Role;
import com.ipl.auth.service.TokenBlocklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request. Reads "Authorization: Bearer <token>", and if it's
 * a valid, non-blocklisted ACCESS token, populates the SecurityContext.
 *
 * A missing, malformed, expired, wrong-type (e.g. a refresh token), or
 * blocklisted token simply leaves the request unauthenticated - the request
 * then falls through to Spring Security's normal 401/403 handling
 * (AuthenticationEntryPoint / AccessDeniedHandler in SecurityConfig) rather
 * than this filter rejecting the request itself.
 *
 * Also implements sliding access-token renewal: if the token presented is
 * close to expiring (see JwtProperties#accessTokenRenewalThreshold), a
 * fresh access token is silently issued in the X-New-Access-Token response
 * header. Clients should check for this header on every response and, if
 * present, replace their stored access token with it - this keeps an
 * active user logged in without a separate /api/auth/refresh round trip.
 * An already-EXPIRED access token is never renewed this way; that still
 * requires a real refresh-token exchange via /api/auth/refresh.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RENEWED_TOKEN_HEADER = "X-New-Access-Token";

    private final JwtService jwtService;
    private final TokenBlocklistService tokenBlocklistService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.parseAndValidate(token, JwtService.TOKEN_TYPE_ACCESS);
            String jti = jwtService.extractJti(claims);

            if (tokenBlocklistService.isBlocklisted(jti)) {
                log.debug("Rejected blocklisted access token jti={}", jti);
                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = jwtService.extractEmail(claims);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            maybeRenewAccessToken(claims, response);
        } catch (RuntimeException ex) {
            // Invalid/expired/wrong-type token: leave the request unauthenticated
            // rather than throwing from inside the filter chain.
            log.debug("JWT authentication skipped: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * If the access token used for this request has less than
     * jwt.access-token-renewal-threshold left before it expires, silently
     * issues a brand-new access token (fresh jti, full expiry window) and
     * returns it via the X-New-Access-Token response header. Does nothing
     * for tokens that still have plenty of life left, and is never reached
     * at all for tokens that have already expired (parseAndValidate above
     * would have thrown first).
     */
    private void maybeRenewAccessToken(Claims claims, HttpServletResponse response) {
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMillis > jwtProperties.getAccessTokenRenewalThreshold()) {
            return;
        }

        Long userId = jwtService.extractUserId(claims);
        String email = jwtService.extractEmail(claims);
        Role role = jwtService.extractRole(claims);

        String renewedToken = jwtService.generateAccessToken(userId, email, role);
        response.setHeader(RENEWED_TOKEN_HEADER, renewedToken);
    }
}