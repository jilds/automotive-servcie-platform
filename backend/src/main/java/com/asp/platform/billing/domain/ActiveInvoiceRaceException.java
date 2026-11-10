package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Two requests raced for the same work order and the unique index chose one. The caller reads the winner. 409 try-again.
public class ActiveInvoiceRaceException extends ConflictException {

    public ActiveInvoiceRaceException() {
        super("try-again", "Another request is issuing this document; try again");
    }
}
