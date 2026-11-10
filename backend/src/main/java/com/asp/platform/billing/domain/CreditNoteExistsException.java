package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// A second credit note with another request id. 409.
public class CreditNoteExistsException extends ConflictException {

    public CreditNoteExistsException() {
        super("credit-note-exists", "The invoice already has a credit note");
    }
}
