package com.ipl.auth.config;

import com.ipl.auth.entity.User;
import com.ipl.auth.enums.Role;
import com.ipl.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time bootstrap of the BCCI ADMIN account on startup.
 *
 * Reads bcci.username / bcci.email / bcci.password (BCCI_USERNAME /
 * BCCI_EMAIL / BCCI_PASSWORD env vars). Uses the SAME User entity, Role
 * enum and PasswordEncoder as normal registration - this only seeds a
 * User row with role=ADMIN. It does not add a second authentication
 * system, and does not touch AuthService/AuthController/the login flow
 * at all. BCCI logs in through the existing POST /api/auth/login with
 * {email, password}, exactly like any other user.
 *
 * Idempotent: if a user with the configured email already exists, nothing
 * is created or modified on subsequent restarts.
 *
 * If BCCI credentials are not fully supplied, this is a no-op - the
 * service starts normally with no ADMIN account (useful for plain local
 * dev where you don't need one yet).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminSeeder implements ApplicationRunner {

    private final BcciProperties bcciProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bcciProperties.isConfigured()) {
            log.info("BCCI bootstrap skipped - bcci.username/email/password not fully configured");
            return;
        }

        if (userRepository.existsByEmail(bcciProperties.getEmail())) {
            log.info("BCCI ADMIN account already exists, skipping bootstrap");
            return;
        }

        User bcciAdmin = User.builder()
                .username(bcciProperties.getUsername())
                .email(bcciProperties.getEmail())
                .password(passwordEncoder.encode(bcciProperties.getPassword()))
                .role(Role.ADMIN)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        try {
            userRepository.save(bcciAdmin);
            log.info("BCCI ADMIN account created for username='{}'", bcciProperties.getUsername());
            // Password is never logged, never stored in plaintext, never returned via any API.
        } catch (DataIntegrityViolationException ex) {
            // Most likely the username is already taken by a different account.
            // Don't fail application startup over a bootstrap conflict - just warn.
            log.warn("BCCI bootstrap could not create the account (likely a username/email conflict): {}", ex.getMessage());
        }
    }
}