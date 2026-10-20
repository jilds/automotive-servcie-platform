package com.asp.workshop;

import java.util.EnumSet;
import java.util.Set;

/** The Kanban columns a work order moves through, and which moves are allowed. */
public enum WorkOrderStatus {
    DIAGNOSTICS,
    AWAITING_APPROVAL,
    AWAITING_PARTS,
    IN_PROGRESS,
    READY,
    DELIVERED,
    CANCELLED;

    public Set<WorkOrderStatus> next() {
        return switch (this) {
            case DIAGNOSTICS -> EnumSet.of(AWAITING_APPROVAL, IN_PROGRESS, CANCELLED);
            case AWAITING_APPROVAL -> EnumSet.of(AWAITING_PARTS, IN_PROGRESS, CANCELLED);
            case AWAITING_PARTS -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(AWAITING_PARTS, READY);
            case READY -> EnumSet.of(IN_PROGRESS, DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(WorkOrderStatus.class);
        };
    }

    public boolean canMoveTo(WorkOrderStatus target) {
        return next().contains(target);
    }

    /** Lines can change until the car is ready for pickup. */
    public boolean isEditable() {
        return this == DIAGNOSTICS || this == AWAITING_APPROVAL || this == AWAITING_PARTS || this == IN_PROGRESS;
    }
}
