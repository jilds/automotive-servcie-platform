package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The quote is no longer SENT: it was decided, superseded or expired. 409 `quote-not-open`.
public class QuoteNotOpenException extends ConflictException {

    public QuoteNotOpenException() {
        super("quote-not-open", "This quote can no longer be decided");
    }
}
