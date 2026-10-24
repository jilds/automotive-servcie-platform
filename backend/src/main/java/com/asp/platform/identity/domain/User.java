package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.Role;
import com.asp.platform.shared.StaleDataException;

/// Someone who signs in to a workshop. Inactive users cannot log in. A user created by the platform admin or an
/// owner has no password (`passwordHash` null, [#pending()]) until they open their activation link.
///
/// @param version how many times the stored row has changed; the owner's role and access changes name the version
///                they read
public record User(UUID id, UUID tenantId, String name, String email, @Nullable String passwordHash, Role role,
        boolean active, Instant createdAt, long version) {

    /// A user not saved yet.
    public User(UUID id, UUID tenantId, String name, String email, @Nullable String passwordHash, Role role,
            boolean active, Instant createdAt) {
        this(id, tenantId, name, email, passwordHash, role, active, createdAt, 0);
    }

    /// A new user who still has to set their password through an activation link.
    public static User invite(UUID id, UUID tenantId, String name, String email, Role role, Instant now) {
        return new User(id, tenantId, name.trim(), normalizeEmail(email), null, role, true, now);
    }

    /// A user who already has a password.
    public static User create(UUID id, UUID tenantId, String name, String email, String passwordHash, Role role,
            Instant now) {
        return new User(id, tenantId, name.trim(), normalizeEmail(email), passwordHash, role, true, now);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public User withAccess(Role newRole, boolean nowActive) {
        return new User(id, tenantId, name, email, passwordHash, newRole, nowActive, createdAt, version);
    }

    /// The user after opening their activation link: their own name and a new password.
    public User activated(String newName, String newPasswordHash) {
        if (newName.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        return new User(id, tenantId, newName.trim(), email, newPasswordHash, role, active, createdAt, version);
    }

    /// @param expectedVersion the version the editor read
    /// @throws StaleDataException when someone else saved the user since
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// True until the user has set a password.
    public boolean pending() {
        return passwordHash == null;
    }

    public boolean isActiveOwner() {
        return active && role == Role.OWNER;
    }
}
