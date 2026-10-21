package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.asp.platform.shared.Role;

/// Someone who signs in to a workshop. Inactive users cannot log in.
public record User(UUID id, UUID tenantId, String name, String email, String passwordHash, Role role, boolean active,
        Instant createdAt) {

    public static User create(UUID id, UUID tenantId, String name, String email, String passwordHash, Role role,
            Instant now) {
        return new User(id, tenantId, name.trim(), normalizeEmail(email), passwordHash, role, true, now);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public User withAccess(Role newRole, boolean nowActive) {
        return new User(id, tenantId, name, email, passwordHash, newRole, nowActive, createdAt);
    }

    public boolean isActiveOwner() {
        return active && role == Role.OWNER;
    }
}
