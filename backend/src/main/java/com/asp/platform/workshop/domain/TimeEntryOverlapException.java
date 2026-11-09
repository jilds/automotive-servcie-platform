package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The time would overlap another entry of the same mechanic. 409 `time-entry-overlap`.
public class TimeEntryOverlapException extends ConflictException {

    public TimeEntryOverlapException() {
        super("time-entry-overlap", "The mechanic already has time booked in that period");
    }
}
