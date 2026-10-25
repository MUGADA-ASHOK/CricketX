package com.ipl.order.security;

public record AuthenticatedUser(
        Long userId,
        String email,
        String role
) {}
