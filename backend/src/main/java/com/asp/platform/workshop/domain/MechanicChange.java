package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One change of the mechanic a work order is assigned to, and why.
///
/// @param fromMechanicId the mechanic before, or null when nobody was assigned
/// @param toMechanicId   the mechanic after, or null when the work order was unassigned
/// @param reason         why; always present when a mechanic was replaced or removed
/// @param changedBy      the owner or front desk user who made the change
public record MechanicChange(UUID id, UUID workOrderId, @Nullable UUID fromMechanicId, @Nullable UUID toMechanicId,
        @Nullable String reason, UUID changedBy, Instant changedAt) {
}
