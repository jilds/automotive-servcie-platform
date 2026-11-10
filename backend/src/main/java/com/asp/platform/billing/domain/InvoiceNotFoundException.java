package com.asp.platform.billing.domain;

import com.asp.platform.shared.NotFoundException;

/// Unknown invoice or one of another workshop. 404 invoice-not-found.
public class InvoiceNotFoundException extends NotFoundException {

    public InvoiceNotFoundException() {
        super("invoice-not-found", "Invoice not found");
    }
}
