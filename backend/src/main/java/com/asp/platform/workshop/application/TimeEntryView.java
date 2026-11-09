package com.asp.platform.workshop.application;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.TimeEntry;

/// A time entry with the names of the mechanic and of who corrected it, and its length at `now`.
public record TimeEntryView(TimeEntry entry, @Nullable String mechanicName, @Nullable String editedByName,
        long seconds) {

    static TimeEntryView of(TimeEntry entry, java.util.Map<java.util.UUID, String> names, Instant now) {
        java.util.UUID editedBy = entry.editedBy();
        return new TimeEntryView(entry, names.get(entry.mechanicId()), editedBy == null ? null : names.get(editedBy),
                entry.seconds(now));
    }
}
