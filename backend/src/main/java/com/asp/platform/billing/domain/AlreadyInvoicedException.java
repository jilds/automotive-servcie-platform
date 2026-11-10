package com.asp.platform.billing.domain;

import java.util.UUID;

import com.asp.platform.shared.ConflictException;

/// The work order has a PENDING, ISSUED or FAILED invoice that another request made. 409 already-invoiced with the
/// invoice id and status as extra properties.
public class AlreadyInvoicedException extends ConflictException {

    private final UUID invoiceId;
    private final InvoiceStatus invoiceStatus;

    public AlreadyInvoicedException(UUID invoiceId, InvoiceStatus invoiceStatus) {
        super("already-invoiced", "This work order is already invoiced");
        this.invoiceId = invoiceId;
        this.invoiceStatus = invoiceStatus;
    }

    public UUID invoiceId() {
        return invoiceId;
    }

    public InvoiceStatus invoiceStatus() {
        return invoiceStatus;
    }
}
