package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One card on the Kanban board.
public record BoardCard(UUID id, int number, @Nullable String plate, String vehicle,
        @Nullable UUID assignedMechanicId, Instant statusChangedAt) {
}
