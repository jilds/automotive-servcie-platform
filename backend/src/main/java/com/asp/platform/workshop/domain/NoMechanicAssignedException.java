package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The owner starts a timer on a work order nobody is assigned to. 409 `no-mechanic-assigned`.
public class NoMechanicAssignedException extends ConflictException {

    public NoMechanicAssignedException() {
        super("no-mechanic-assigned", "Assign a mechanic before starting a timer");
    }
}
