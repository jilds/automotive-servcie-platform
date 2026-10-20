package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

public class WorkOrderNotFoundException extends NotFoundException {

    public WorkOrderNotFoundException() {
        super("work-order-not-found", "Work order not found");
    }
}
