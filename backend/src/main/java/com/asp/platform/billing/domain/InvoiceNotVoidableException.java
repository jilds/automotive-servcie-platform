package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Void of anything but an ISSUED invoice without a credit note. 409.
public class InvoiceNotVoidableException extends ConflictException {

    public InvoiceNotVoidableException() {
        super("invoice-not-voidable", "Only an issued invoice without a credit note can be voided");
    }
}
