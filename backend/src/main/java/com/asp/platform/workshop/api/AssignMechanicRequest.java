package com.asp.platform.workshop.api;

import java.util.UUID;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.WorkOrder;

/// A null mechanic unassigns the work order. `reason` is required when an assigned mechanic is replaced or removed
/// (400 `reason-required`), optional for a first assignment, at most 500 characters.
record AssignMechanicRequest(@Nullable UUID mechanicId,
        @Nullable @Size(max = WorkOrder.MAX_REASON_LENGTH) String reason) {
}
