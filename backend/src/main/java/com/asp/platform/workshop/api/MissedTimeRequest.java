package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.TimeEntry;

/// `POST /work-orders/{id}/time-entries`: a stopped entry the mechanic forgot to record. A blank reason answers 400
/// `reason-required` (checked by the entry).
record MissedTimeRequest(
        @NotNull UUID mechanicId,
        @Nullable UUID lineId,
        @NotNull Instant startedAt,
        @NotNull Instant stoppedAt,
        @Nullable @Size(max = TimeEntry.MAX_REASON_LENGTH) String reason) {
}
