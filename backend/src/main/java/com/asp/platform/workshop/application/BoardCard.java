package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One card on the Kanban board. The plate is null when the vehicle is not found.
public record BoardCard(UUID id, int number, @Nullable VehiclePlate plate, String vehicle,
        @Nullable UUID assignedMechanicId, Instant statusChangedAt) {
}
