package com.ipl.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.PublicKey;
import java.util.List;

/**
 * Validates access tokens issued by ipl-auth-service using the RSA Public Key.
 *
 * Signature, structure and expiration are verified with the RSA Public Key (RS256).
 * Only access tokens with type=access are accepted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final GatewayJwtProperties jwtProperties;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.publicKey = RsaKeyUtils.parsePublicKey(jwtProperties.getPublicKey());
        log.info("Initialized RSA RS256 public key verifier for API Gateway");
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> validate(token))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Authentication validate(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid or expired access token", ex);
        }

        String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(type)) {
            throw new BadCredentialsException("Token is not an access token");
        }

        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null || role.isBlank()) {
            throw new BadCredentialsException("Token is missing the role claim");
        }

        String email = claims.get(CLAIM_EMAIL, String.class);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        return new UsernamePasswordAuthenticationToken(email, null, authorities);
    }
}
