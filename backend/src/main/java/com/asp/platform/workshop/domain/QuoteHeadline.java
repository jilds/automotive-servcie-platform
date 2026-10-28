package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// The latest quote of a work order, without amounts or lines: what the work order screen and the board show, also
/// to mechanics.
///
/// @param status the stored status; read [#statusAt] for what to show
public record QuoteHeadline(UUID id, UUID workOrderId, int number, QuoteStatus status, Instant validUntil,
        @Nullable Instant decidedAt, @Nullable QuoteChannel decidedChannel) {

    /// EXPIRED for a SENT quote past its validity, else the stored status.
    public QuoteStatus statusAt(Instant now) {
        return Quote.effectiveStatus(status, validUntil, now);
    }
}
