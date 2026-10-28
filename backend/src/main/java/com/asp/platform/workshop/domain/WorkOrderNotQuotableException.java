package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// A quote is sent only in DIAGNOSTICS or AWAITING_APPROVAL and for a work order with lines. 409
/// `work-order-not-quotable`.
public class WorkOrderNotQuotableException extends ConflictException {

    public WorkOrderNotQuotableException() {
        super("work-order-not-quotable", "A quote needs a work order in diagnostics or awaiting approval, with lines");
    }
}
