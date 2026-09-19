package com.ipl.auth.dto.response;

import com.ipl.auth.entity.User;

/**
 * Safe, external view of a User - never includes the password hash.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String role
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
