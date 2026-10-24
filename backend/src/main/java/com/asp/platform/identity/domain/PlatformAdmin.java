package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.UUID;

/// Someone who runs ASP itself: registers workshops and manages their subscription and theme. Belongs to no
/// workshop and can never read a workshop's customers, vehicles or work orders.
public record PlatformAdmin(UUID id, String name, String email, String passwordHash, boolean active,
        Instant createdAt) {

    public static PlatformAdmin create(UUID id, String name, String email, String passwordHash, Instant now) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        return new PlatformAdmin(id, name.trim(), User.normalizeEmail(email), passwordHash, true, now);
    }
}
