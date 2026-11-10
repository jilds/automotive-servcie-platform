package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Void of an invoice with a PAID payment. 409.
public class InvoiceHasPaymentsException extends ConflictException {

    public InvoiceHasPaymentsException() {
        super("invoice-has-payments", "The invoice has payments; issue a credit note instead");
    }
}
