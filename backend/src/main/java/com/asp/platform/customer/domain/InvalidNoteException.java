package com.asp.platform.customer.domain;

import com.asp.platform.shared.InvalidInputException;

/// An observation or note is empty or longer than 2000 characters. Mapped to 400 `invalid-note` on the field `body`.
public class InvalidNoteException extends InvalidInputException {

    public static final String CODE = "invalid-note";

    public InvalidNoteException() {
        super(CODE, "body", "A note has 1 to 2000 characters");
    }
}
