package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A workshop subscribed to ASP.
public record Tenant(UUID id, String name, @Nullable String nif, String country, String timezone, Instant createdAt) {

    private static final String PORTUGAL = "PT";
    private static final String LISBON = "Europe/Lisbon";

    /// Release 1 sells in Portugal only, so new workshops start with Portuguese defaults.
    public static Tenant register(UUID id, String name, @Nullable String nif, Instant now) {
        return new Tenant(id, name.trim(), nif, PORTUGAL, LISBON, now);
    }
}
