package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.QuoteStatus;

/// One card on the Kanban board. The plate is null when the vehicle is not found. `quoteStatus` is the status of the
/// order's latest quote (EXPIRED for a SENT quote past its validity), or null when none was sent.
public record BoardCard(UUID id, int number, @Nullable VehiclePlate plate, String vehicle,
        @Nullable UUID assignedMechanicId, Instant statusChangedAt, @Nullable QuoteStatus quoteStatus) {
}
