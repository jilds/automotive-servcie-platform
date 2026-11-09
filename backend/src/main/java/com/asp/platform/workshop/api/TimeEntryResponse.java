package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.TimeEntryView;
import com.asp.platform.workshop.domain.TimeEntry;

/// One time entry. `seconds` runs up to the time of the response for a running entry. `editedByName`, `editedAt` and
/// `editReason` describe the last correction, null when the entry was never corrected.
record TimeEntryResponse(UUID id, UUID workOrderId, @Nullable UUID lineId, UUID mechanicId,
        @Nullable String mechanicName, Instant startedAt, @Nullable Instant stoppedAt, long seconds, boolean running,
        boolean autoStopped, @Nullable String editedByName, @Nullable Instant editedAt, @Nullable String editReason,
        long version) {

    static TimeEntryResponse of(TimeEntryView view) {
        TimeEntry e = view.entry();
        return new TimeEntryResponse(e.id(), e.workOrderId(), e.lineId(), e.mechanicId(), view.mechanicName(),
                e.startedAt(), e.stoppedAt(), view.seconds(), e.isRunning(), e.autoStopped(), view.editedByName(),
                e.editedAt(), e.editReason(), e.version());
    }
}
