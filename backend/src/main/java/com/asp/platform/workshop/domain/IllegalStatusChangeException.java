package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

public class IllegalStatusChangeException extends ConflictException {

    public IllegalStatusChangeException(WorkOrderStatus from, WorkOrderStatus to) {
        super("illegal-status-change", "Cannot move work order from " + from + " to " + to);
    }
}
