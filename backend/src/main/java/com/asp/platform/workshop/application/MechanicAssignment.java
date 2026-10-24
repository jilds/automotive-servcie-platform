package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Who works on a work order now, and every change of mechanic with its reason, oldest first. Names come from the
/// team (also for people no longer active); a name is null only when the user no longer exists.
public record MechanicAssignment(@Nullable UUID mechanicId, @Nullable String mechanicName, List<Change> changes) {

    public record Change(@Nullable UUID fromMechanicId, @Nullable String fromName, @Nullable UUID toMechanicId,
            @Nullable String toName, @Nullable String reason, UUID changedById, @Nullable String changedByName,
            Instant changedAt) {
    }
}
