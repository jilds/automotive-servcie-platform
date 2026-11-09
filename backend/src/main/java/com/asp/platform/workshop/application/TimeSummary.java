package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// The time booked on a work order: every entry (newest start first), the totals at `now`, and the running entry.
///
/// @param unassignedSeconds time on no line of the order
/// @param byLine            one item per line of the order that has time
public record TimeSummary(Instant now, long totalSeconds, long unassignedSeconds, List<LineSeconds> byLine,
        @Nullable TimeEntryView running, List<TimeEntryView> entries) {

    public record LineSeconds(UUID lineId, long seconds) {
    }
}
