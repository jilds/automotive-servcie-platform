package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Retry of a document that is not PENDING or FAILED. 409.
public class InvoiceNotRetryableException extends ConflictException {

    public InvoiceNotRetryableException() {
        super("invoice-not-retryable", "Only a pending or failed document can be retried");
    }
}
