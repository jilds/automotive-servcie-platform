package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.MechanicAssignment;

/// The current mechanic of a work order and every change with who made it and why, oldest first.
record MechanicAssignmentResponse(@Nullable UUID mechanicId, @Nullable String mechanicName, List<Change> changes) {

    record Change(@Nullable UUID fromMechanicId, @Nullable String fromName, @Nullable UUID toMechanicId,
            @Nullable String toName, @Nullable String reason, UUID changedById, @Nullable String changedByName,
            Instant changedAt) {
    }

    static MechanicAssignmentResponse of(MechanicAssignment a) {
        return new MechanicAssignmentResponse(a.mechanicId(), a.mechanicName(), a.changes().stream()
                .map(c -> new Change(c.fromMechanicId(), c.fromName(), c.toMechanicId(), c.toName(), c.reason(),
                        c.changedById(), c.changedByName(), c.changedAt()))
                .toList());
    }
}
