package com.ipl.auth.config;

import com.ipl.auth.entity.User;
import com.ipl.auth.enums.Role;
import com.ipl.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures only the master BCCI Admin account exists on startup.
 * All franchise reps and fan users must register manually via the application frontend.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedBcciAdmin("bcci@123", "bcci.official@gmail.com", "bcc@virat@123");
    }

    private void seedBcciAdmin(String username, String email, String rawPassword) {
        userRepository.findByEmail(email)
                .or(() -> userRepository.findByUsername(username))
                .ifPresentOrElse(
                        user -> {
                            boolean changed = false;
                            if (user.getRole() != Role.ADMIN) {
                                user.setRole(Role.ADMIN);
                                changed = true;
                            }
                            if (rawPassword != null && !passwordEncoder.matches(rawPassword, user.getPassword())) {
                                user.setPassword(passwordEncoder.encode(rawPassword));
                                changed = true;
                            }
                            if (!user.getUsername().equals(username)) {
                                user.setUsername(username);
                                changed = true;
                            }
                            if (changed) {
                                userRepository.save(user);
                                log.info("Synchronized BCCI Admin account: {} ({})", username, email);
                            }
                        },
                        () -> {
                            User bcciAdmin = User.builder()
                                    .username(username)
                                    .email(email)
                                    .password(passwordEncoder.encode(rawPassword))
                                    .role(Role.ADMIN)
                                    .enabled(true)
                                    .accountNonLocked(true)
                                    .build();
                            userRepository.save(bcciAdmin);
                            log.info("Initialized BCCI Admin account: {} ({}) with Role.ADMIN", username, email);
                        }
                );
    }
}
