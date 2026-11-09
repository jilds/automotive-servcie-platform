package com.asp.platform.workshop.application;

import org.jspecify.annotations.Nullable;

/// The signed-in user's running entry with the work order it is on; all null when nothing runs.
public record RunningTimer(@Nullable TimeEntryView entry, @Nullable Integer workOrderNumber,
        @Nullable String plateDisplay) {

    static RunningTimer none() {
        return new RunningTimer(null, null, null);
    }
}
