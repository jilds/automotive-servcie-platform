package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.TimeEntryChangeView;

/// What a time entry was before one correction, and who changed it, when and why.
record TimeEntryChangeResponse(UUID id, Instant oldStartedAt, @Nullable Instant oldStoppedAt,
        @Nullable UUID oldLineId, @Nullable String changedByName, Instant changedAt, String reason) {

    static TimeEntryChangeResponse of(TimeEntryChangeView view) {
        var c = view.change();
        return new TimeEntryChangeResponse(c.id(), c.oldStartedAt(), c.oldStoppedAt(), c.oldLineId(),
                view.changedByName(), c.changedAt(), c.reason());
    }
}
