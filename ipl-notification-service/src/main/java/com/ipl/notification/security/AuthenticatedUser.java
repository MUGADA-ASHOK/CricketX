package com.ipl.notification.security;

public record AuthenticatedUser(Long userId, String email, String role) {
}

