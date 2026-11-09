package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// Stop on an order with no running timer. 409 `timer-not-running`.
public class TimerNotRunningException extends ConflictException {

    public TimerNotRunningException() {
        super("timer-not-running", "No timer is running on this work order");
    }
}
