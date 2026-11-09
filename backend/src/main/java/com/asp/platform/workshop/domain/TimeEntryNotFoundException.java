package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

/// No such time entry in the workshop, or not an entry of the given work order. 404 `time-entry-not-found`.
public class TimeEntryNotFoundException extends NotFoundException {

    public TimeEntryNotFoundException() {
        super("time-entry-not-found", "Time entry not found");
    }
}
