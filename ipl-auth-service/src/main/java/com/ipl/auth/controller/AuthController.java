package com.ipl.auth.controller;

import com.ipl.auth.dto.request.LoginRequest;
import com.ipl.auth.dto.request.LogoutRequest;
import com.ipl.auth.dto.request.RefreshTokenRequest;
import com.ipl.auth.dto.request.RegisterRequest;
import com.ipl.auth.dto.response.ApiResponse;
import com.ipl.auth.dto.response.AuthResponse;
import com.ipl.auth.dto.response.UserResponse;
import com.ipl.auth.exception.InvalidTokenException;
import com.ipl.auth.security.UserPrincipal;
import com.ipl.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse auth = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", auth));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody(required = false) LogoutRequest request) {

        String accessToken = extractBearerToken(authorizationHeader);
        String refreshToken = request != null ? request.refreshToken() : null;

        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse user = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Current user", user));
    }

    // -------------------------------------------------------------------
    // Demo role-protected endpoints - for verifying role-based
    // authorization only. Safe to delete once the real business endpoints
    // (in the Event/Order services) take over this role.
    // -------------------------------------------------------------------

    @GetMapping("/admin/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> adminOnlyTest() {
        return ResponseEntity.ok(ApiResponse.success("Admin access granted", "OK"));
    }

    @GetMapping("/franchise/test")
    @PreAuthorize("hasAnyRole('ADMIN','FRANCHISE')")
    public ResponseEntity<ApiResponse<String>> franchiseTest() {
        return ResponseEntity.ok(ApiResponse.success("Franchise access granted", "OK"));
    }

    @GetMapping("/user/test")
    @PreAuthorize("hasAnyRole('ADMIN','FRANCHISE','USER')")
    public ResponseEntity<ApiResponse<String>> userTest() {
        return ResponseEntity.ok(ApiResponse.success("User access granted", "OK"));
    }

    private String extractBearerToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException("Missing or malformed Authorization header");
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
