package com.asp.platform.workshop.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A labor or part line to add to a work order. Money is in integer cents.
///
/// @param serviceItemId the catalogue service the line was added from, or null for a line typed by hand
/// @param productId     the stock product the line reserves, or null when it does not come from stock
public record NewLine(LineType type, String description, BigDecimal quantity, long unitPriceCents,
        @Nullable BigDecimal discountPercent, BigDecimal vatRate, @Nullable UUID serviceItemId,
        @Nullable UUID productId) {

    /// A line typed by hand.
    public NewLine(LineType type, String description, BigDecimal quantity, long unitPriceCents,
            @Nullable BigDecimal discountPercent, BigDecimal vatRate) {
        this(type, description, quantity, unitPriceCents, discountPercent, vatRate, null, null);
    }

    /// A line from the catalogue (not from stock).
    public NewLine(LineType type, String description, BigDecimal quantity, long unitPriceCents,
            @Nullable BigDecimal discountPercent, BigDecimal vatRate, @Nullable UUID serviceItemId) {
        this(type, description, quantity, unitPriceCents, discountPercent, vatRate, serviceItemId, null);
    }
}
