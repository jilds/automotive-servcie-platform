package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The quote is SENT but its validity has passed. 409 `quote-expired`.
public class QuoteExpiredException extends ConflictException {

    public QuoteExpiredException() {
        super("quote-expired", "This quote has expired");
    }
}
