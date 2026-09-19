package com.ipl.auth.service;

import com.ipl.auth.config.JwtProperties;
import com.ipl.auth.dto.request.LoginRequest;
import com.ipl.auth.dto.request.RegisterRequest;
import com.ipl.auth.dto.response.AuthResponse;
import com.ipl.auth.dto.response.UserResponse;
import com.ipl.auth.entity.User;
import com.ipl.auth.enums.Role;
import com.ipl.auth.exception.EmailAlreadyExistsException;
import com.ipl.auth.exception.ResourceNotFoundException;
import com.ipl.auth.exception.UsernameAlreadyExistsException;
import com.ipl.auth.repository.UserRepository;
import com.ipl.auth.security.JwtService;
import com.ipl.auth.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlocklistService tokenBlocklistService;
    private final JwtProperties jwtProperties;

    /**
     * Public self-registration. Always creates a USER account - RegisterRequest
     * has no "role" field at all, so there is no way for a client to request
     * ADMIN/FRANCHISE through this endpoint.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException("This username is already taken");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RefreshTokenService.TokenPair tokens = refreshTokenService.issueTokens(user);
        return toAuthResponse(tokens);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.TokenPair tokens = refreshTokenService.rotate(rawRefreshToken);
        return toAuthResponse(tokens);
    }

    /**
     * Blocklists the current access token's JTI (so it's immediately
     * unusable even though it hasn't expired yet) and, if a refresh token
     * is supplied, revokes that session too.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        Claims claims = jwtService.parseAndValidate(accessToken, JwtService.TOKEN_TYPE_ACCESS);
        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        tokenBlocklistService.blocklist(jwtService.extractJti(claims), remainingMillis);

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeSession(refreshToken);
        }
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserResponse.fromEntity(user);
    }

    private AuthResponse toAuthResponse(RefreshTokenService.TokenPair tokens) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                jwtProperties.getAccessTokenExpiration() / 1000,
                jwtProperties.getRefreshTokenExpiration() / 1000,
                UserResponse.fromEntity(tokens.user())
        );
    }
}
