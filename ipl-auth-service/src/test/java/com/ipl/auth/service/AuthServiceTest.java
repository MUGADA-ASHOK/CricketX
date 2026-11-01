package com.ipl.auth.service;

import com.ipl.auth.config.JwtProperties;
import com.ipl.auth.dto.request.LoginRequest;
import com.ipl.auth.dto.request.RegisterRequest;
import com.ipl.auth.dto.response.AuthResponse;
import com.ipl.auth.dto.response.UserResponse;
import com.ipl.auth.entity.User;
import com.ipl.auth.enums.Role;
import com.ipl.auth.exception.EmailAlreadyExistsException;
import com.ipl.auth.repository.UserRepository;
import com.ipl.auth.security.JwtService;
import com.ipl.auth.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenBlocklistService tokenBlocklistService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("fan_user")
                .email("fan@gmail.com")
                .password("encoded_pass")
                .role(Role.USER)
                .enabled(true)
                .accountNonLocked(true)
                .build();
    }

    @Test
    @DisplayName("Should register public fan with ROLE_USER")
    void testRegisterFan_Success() {
        RegisterRequest request = new RegisterRequest("fan_user", "fan@gmail.com", "Password@123");

        when(userRepository.existsByEmail("fan@gmail.com")).thenReturn(false);
        when(userRepository.existsByUsername("fan_user")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse response = authService.register(request);

        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.email()).isEqualTo("fan@gmail.com");
    }

    @Test
    @DisplayName("Should register franchise account with ROLE_FRANCHISE")
    void testRegisterFranchise_Success() {
        RegisterRequest request = new RegisterRequest("csk_admin", "admin@csk.com", "Csk@2026!");
        User franchiseUser = User.builder().id(2L).username("csk_admin").email("admin@csk.com").role(Role.FRANCHISE).build();

        when(userRepository.existsByEmail("admin@csk.com")).thenReturn(false);
        when(userRepository.existsByUsername("csk_admin")).thenReturn(false);
        when(passwordEncoder.encode("Csk@2026!")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(franchiseUser);

        UserResponse response = authService.registerFranchise(request);

        assertThat(response.role()).isEqualTo("FRANCHISE");
    }

    @Test
    @DisplayName("Should reject registration when email already exists")
    void testRegister_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("fan_user", "fan@gmail.com", "Password@123");
        when(userRepository.existsByEmail("fan@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should authenticate user and issue access and refresh token pair")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest("fan@gmail.com", "Password@123");
        UserPrincipal principal = new UserPrincipal(1L, "fan_user", "fan@gmail.com", "encoded", Role.USER);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(refreshTokenService.issueTokens(sampleUser)).thenReturn(
                new RefreshTokenService.TokenPair("mock_access_token", "mock_refresh_token", sampleUser));
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("mock_access_token");
        assertThat(response.refreshToken()).isEqualTo("mock_refresh_token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("Should blocklist token in Redis on logout with remaining expiration TTL")
    void testLogout_BlocklistsTokenInRedis() {
        String accessToken = "valid.jwt.token";
        String refreshToken = "valid.refresh.token";

        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60000));
        when(jwtService.parseAndValidate(accessToken, JwtService.TOKEN_TYPE_ACCESS)).thenReturn(claims);
        when(jwtService.extractJti(claims)).thenReturn("jti-uuid-123");

        authService.logout(accessToken, refreshToken);

        verify(tokenBlocklistService).blocklist(eq("jti-uuid-123"), anyLong());
        verify(refreshTokenService).revokeSession(refreshToken);
    }
}

