package com.asp.platform.workshop.api;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.RunningTimer;

/// The signed-in user's running entry and the order it is on; all null when nothing runs.
record RunningTimerResponse(@Nullable TimeEntryResponse entry, @Nullable Integer workOrderNumber,
        @Nullable String plateDisplay) {

    static RunningTimerResponse of(RunningTimer timer) {
        return new RunningTimerResponse(timer.entry() == null ? null : TimeEntryResponse.of(timer.entry()),
                timer.workOrderNumber(), timer.plateDisplay());
    }
}
