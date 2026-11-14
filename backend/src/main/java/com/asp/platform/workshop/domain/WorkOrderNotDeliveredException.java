package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// Only a delivered work order can be hidden from the board.
public class WorkOrderNotDeliveredException extends ConflictException {

    public WorkOrderNotDeliveredException() {
        super("work-order-not-delivered", "Only a delivered work order can be hidden from the board");
    }
}
