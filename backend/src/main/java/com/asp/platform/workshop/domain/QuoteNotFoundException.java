package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

/// No such quote in the workshop, not a quote of the given work order, or an unknown status token. 404
/// `quote-not-found`.
public class QuoteNotFoundException extends NotFoundException {

    public QuoteNotFoundException() {
        super("quote-not-found", "Quote not found");
    }
}
