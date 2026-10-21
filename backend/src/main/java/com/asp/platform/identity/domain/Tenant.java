package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A workshop subscribed to ASP.
public record Tenant(UUID id, WorkshopSettings settings, Instant createdAt) {

    public static Tenant register(UUID id, String name, @Nullable String nif, Instant now) {
        return new Tenant(id, WorkshopSettings.portugueseDefaults(name, nif), now);
    }

    public Tenant withSettings(WorkshopSettings newSettings) {
        return new Tenant(id, newSettings, createdAt);
    }
}
