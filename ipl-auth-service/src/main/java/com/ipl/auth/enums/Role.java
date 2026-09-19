package com.ipl.auth.enums;

/**
 * Business roles supported by the platform.
 *
 * Stored in the database as a STRING (@Enumerated(EnumType.STRING)) so the
 * column is human-readable and safe to reorder/extend without breaking data.
 *
 * When exposed to Spring Security, these are prefixed with "ROLE_" in the
 * security package (UserPrincipal) so that hasRole('ADMIN') works
 * consistently instead of mixing "ADMIN" / "ROLE_ADMIN" conventions.
 */
public enum Role {
    ADMIN,
    FRANCHISE,
    USER
}
