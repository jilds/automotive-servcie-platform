package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// What a time entry was before one correction, and who changed it, when and why. Only inserted.
public record TimeEntryChange(UUID id, UUID entryId, Instant oldStartedAt, @Nullable Instant oldStoppedAt,
        @Nullable UUID oldLineId, UUID changedBy, Instant changedAt, String reason) {
}
