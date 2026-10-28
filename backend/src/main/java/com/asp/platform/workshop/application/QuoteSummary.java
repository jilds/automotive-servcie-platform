package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.QuoteChannel;
import com.asp.platform.workshop.domain.QuoteHeadline;
import com.asp.platform.workshop.domain.QuoteStatus;

/// The latest quote of a work order as the work order screen shows it, also to mechanics: no amounts, no lines.
///
/// @param status EXPIRED for a SENT quote past `validUntil`
public record QuoteSummary(UUID id, int number, QuoteStatus status, Instant validUntil, @Nullable Instant decidedAt,
        @Nullable QuoteChannel decidedChannel) {

    static QuoteSummary of(QuoteHeadline quote, Instant now) {
        return new QuoteSummary(quote.id(), quote.number(), quote.statusAt(now), quote.validUntil(),
                quote.decidedAt(), quote.decidedChannel());
    }
}
