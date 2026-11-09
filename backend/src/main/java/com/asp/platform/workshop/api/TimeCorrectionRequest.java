package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.TimeEntry;

/// `PUT /work-orders/{id}/time-entries/{entryId}`: the entry as it should be. `stoppedAt` null keeps a running entry
/// running (a stopped entry needs one); `lineId` null books the time on the whole order. A blank reason answers 400
/// `reason-required` (checked by the entry).
record TimeCorrectionRequest(
        @NotNull Instant startedAt,
        @Nullable Instant stoppedAt,
        @Nullable UUID lineId,
        @Nullable @Size(max = TimeEntry.MAX_REASON_LENGTH) String reason,
        @NotNull Long version) {
}
