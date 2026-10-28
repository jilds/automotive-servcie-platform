package com.asp.platform.workshop.domain;

/// SENT → APPROVED | REJECTED | SUPERSEDED | EXPIRED; nothing after that.
public enum QuoteStatus {
    SENT,
    APPROVED,
    REJECTED,
    SUPERSEDED,
    EXPIRED
}
