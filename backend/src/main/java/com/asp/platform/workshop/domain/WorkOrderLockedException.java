package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

public class WorkOrderLockedException extends ConflictException {

    public WorkOrderLockedException(WorkOrderStatus status) {
        super("work-order-locked", "Work order is " + status + " and can no longer be changed");
    }
}
