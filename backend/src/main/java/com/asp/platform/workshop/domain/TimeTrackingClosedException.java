package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// A timer starts only while the work order is not READY, DELIVERED or CANCELLED. 409 `time-tracking-closed`.
public class TimeTrackingClosedException extends ConflictException {

    public TimeTrackingClosedException(WorkOrderStatus status) {
        super("time-tracking-closed", "Work order is " + status + "; no timer can start");
    }
}
