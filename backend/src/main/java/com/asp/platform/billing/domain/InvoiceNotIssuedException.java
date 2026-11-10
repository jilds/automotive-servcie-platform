package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// The operation needs an issued invoice without a credit note. 409.
public class InvoiceNotIssuedException extends ConflictException {

    public InvoiceNotIssuedException() {
        super("invoice-not-issued", "The document is not an issued invoice");
    }
}
