package com.asp.platform.workshop.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteStatus;

/// A quote for the front desk, with the names of who sent it and who recorded the decision.
///
/// @param status EXPIRED for a SENT quote past `validUntil`, else the stored status
public record QuoteView(Quote quote, QuoteStatus status, @Nullable String sentByName,
        @Nullable String decidedByName) {
}
