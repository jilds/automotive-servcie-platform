package com.asp.platform.booking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/// A service the customer booked, as the catalogue offered it then: the price shown on the confirmation stays even
/// when the catalogue changes later.
///
/// @param priceCents price before VAT, in cents
/// @param vatRate    VAT percentage
public record BookedService(UUID serviceItemId, String name, int durationMinutes, long priceCents,
        BigDecimal vatRate) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /// The price with VAT, rounded half up to cents.
    public long priceWithVatCents() {
        return BigDecimal.valueOf(priceCents).multiply(HUNDRED.add(vatRate)).divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
