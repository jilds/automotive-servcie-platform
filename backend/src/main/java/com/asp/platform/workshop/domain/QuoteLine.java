package com.asp.platform.workshop.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A work order line as it was when the quote was sent; never changed afterwards.
///
/// @param position      1, 2, … in the order of the work order's lines
/// @param serviceItemId the catalogue service the line came from, or null
/// @param productId     the stock product the line reserves, or null (a part typed by hand when the type is PART)
public record QuoteLine(UUID id, int position, LineType type, String description, BigDecimal quantity,
        long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate, long netCents, long vatCents,
        @Nullable UUID serviceItemId, @Nullable UUID productId) {

    static QuoteLine of(UUID id, int position, WorkOrderLine line) {
        return new QuoteLine(id, position, line.type(), line.description(), line.quantity(), line.unitPriceCents(),
                line.discountPercent(), line.vatRate(), line.netCents(), line.vatCents(), line.serviceItemId(),
                line.productId());
    }

    /// A part typed by hand, which probably still has to be ordered.
    public boolean isPartToOrder() {
        return type == LineType.PART && productId == null;
    }
}
