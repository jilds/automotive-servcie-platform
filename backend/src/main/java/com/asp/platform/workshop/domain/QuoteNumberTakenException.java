package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// Two quotes of the workshop were sent at the same moment and got the same number; nothing was stored. 409
/// `try-again`, like a lock timeout: the client may simply send again.
public class QuoteNumberTakenException extends ConflictException {

    public QuoteNumberTakenException() {
        super("try-again", "Another quote was sent at the same moment; try again");
    }
}
