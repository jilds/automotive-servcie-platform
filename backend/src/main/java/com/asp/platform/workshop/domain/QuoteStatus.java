package com.asp.platform.workshop.domain;

/// SENT → APPROVED | REJECTED | SUPERSEDED | EXPIRED; nothing after that.
public enum QuoteStatus {
    SENT,
    APPROVED,
    REJECTED,
    SUPERSEDED,
    EXPIRED;

    /// True once the customer (on the link) or the staff (for the customer) approved or rejected the quote. A SENT,
    /// SUPERSEDED or EXPIRED quote is not an answer.
    public boolean isAnswered() {
        return this == APPROVED || this == REJECTED;
    }
}
