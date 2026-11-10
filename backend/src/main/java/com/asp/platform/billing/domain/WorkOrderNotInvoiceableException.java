package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Only a DELIVERED work order can be invoiced. 409 work-order-not-invoiceable with the order status.
public class WorkOrderNotInvoiceableException extends ConflictException {

    private final String status;

    public WorkOrderNotInvoiceableException(String status) {
        super("work-order-not-invoiceable", "Only a delivered work order can be invoiced");
        this.status = status;
    }

    public String status() {
        return status;
    }
}
