package com.asp.platform.billing.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A line as it was on the work order when the invoice was issued. The amounts are the work order's own, rounded line
/// by line half up, so the invoice total equals the total the customer saw on the work order and the quote.
///
/// @param sourceLineId the work order line it was copied from (no foreign key: the snapshot outlives the line)
public record InvoiceLine(UUID id, int position, LineKind kind, String description, BigDecimal quantity,
        long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate, long netCents, long vatCents,
        @Nullable UUID sourceLineId) {

    public long totalCents() {
        return netCents + vatCents;
    }
}
