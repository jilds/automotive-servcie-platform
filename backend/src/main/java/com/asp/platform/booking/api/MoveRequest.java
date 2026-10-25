package com.asp.platform.booking.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/// `{ "slotStart": "2026-11-02T10:00:00Z", "version": 1 }`.
record MoveRequest(@NotNull Instant slotStart, @NotNull @Nullable Long version) {

    long requiredVersion() {
        return Versions.required(version);
    }
}
