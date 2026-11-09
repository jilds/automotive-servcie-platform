package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.TimeSummary;

/// The time of a work order at `now`; clients tick the running clock locally from `now` and `running.startedAt`.
record TimeSummaryResponse(Instant now, long totalSeconds, long unassignedSeconds, List<LineSeconds> byLine,
        @Nullable TimeEntryResponse running, List<TimeEntryResponse> entries) {

    record LineSeconds(UUID lineId, long seconds) {
    }

    static TimeSummaryResponse of(TimeSummary summary) {
        return new TimeSummaryResponse(summary.now(), summary.totalSeconds(), summary.unassignedSeconds(),
                summary.byLine().stream().map(l -> new LineSeconds(l.lineId(), l.seconds())).toList(),
                summary.running() == null ? null : TimeEntryResponse.of(summary.running()),
                summary.entries().stream().map(TimeEntryResponse::of).toList());
    }
}
