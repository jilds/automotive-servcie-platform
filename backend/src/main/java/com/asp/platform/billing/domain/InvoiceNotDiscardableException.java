package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Discard of a document that is not FAILED. 409.
public class InvoiceNotDiscardableException extends ConflictException {

    public InvoiceNotDiscardableException() {
        super("invoice-not-discardable", "Only a failed document can be discarded");
    }
}
