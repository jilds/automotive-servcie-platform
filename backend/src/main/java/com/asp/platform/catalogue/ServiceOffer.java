package com.asp.platform.catalogue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A catalogue service as other modules show or book it (online booking). Money in cents.
///
/// @param durationMinutes how long the car is expected to be in the workshop
/// @param priceCents      the effective price before VAT: the fixed price, or the standard hours times the
///                        workshop's labour rate as it is now
/// @param vatRate         VAT percentage
/// @param active          still offered; inactive services can no longer be booked or added
/// @param listedOnline    shown on the workshop's public pages
public record ServiceOffer(UUID id, String name, @Nullable String description, @Nullable String category,
        int durationMinutes, long priceCents, BigDecimal vatRate, boolean active, boolean listedOnline) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /// Offered on the public pages: active and listed online.
    public boolean bookableOnline() {
        return active && listedOnline;
    }

    /// The price with VAT, rounded half up to cents.
    public long priceWithVatCents() {
        return BigDecimal.valueOf(priceCents).multiply(HUNDRED.add(vatRate)).divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
