package com.asp.platform.catalogue.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;

/// What the workshop offers as a standard service.
///
/// @param standardHours   planned labour time, for planning and productivity; with [ServicePricing#HOURLY] also the
///                        hours that are charged
/// @param pricing         a fixed price, or the standard hours times the workshop's labour rate
/// @param priceCents      fixed price before VAT, in cents; kept but not used while the pricing is hourly
/// @param vatRate         VAT percentage, 0 to 100
/// @param durationMinutes how long the car is expected to be in the workshop, for booking slots
/// @param active          inactive services stay on old work orders but can no longer be added
/// @param listedOnline    shown on the workshop's public pages (booking, shop) when those are switched on
public record ServiceDetails(String name, @Nullable String description, @Nullable String category,
        BigDecimal standardHours, ServicePricing pricing, long priceCents, BigDecimal vatRate, int durationMinutes,
        boolean active, boolean listedOnline) {

    public static final int MAX_NAME = 120;
    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_CATEGORY = 60;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ServiceDetails {
        name = Text.required(name, "Name", MAX_NAME);
        description = Text.optional(description, "Description", MAX_DESCRIPTION);
        category = Text.optional(category, "Category", MAX_CATEGORY);
        if (standardHours.signum() < 0) {
            throw new IllegalArgumentException("Standard hours cannot be negative");
        }
        if (priceCents < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (vatRate.signum() < 0 || vatRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        if (durationMinutes < 5 || durationMinutes > 1440) {
            throw new IllegalArgumentException("Duration must be between 5 minutes and 24 hours");
        }
    }

    public boolean hourly() {
        return pricing == ServicePricing.HOURLY;
    }

    /// The price before VAT in cents: the fixed price, or the standard hours times the labour rate, rounded half up
    /// to cents (0.75 h at 45.00/h is 33.75; 1.25 h at 40.10/h is 50.125, so 50.13).
    ///
    /// @param labourRateCents the workshop's hourly labour rate in cents; only used for hourly pricing
    public long effectivePriceCents(long labourRateCents) {
        return switch (pricing) {
            case FIXED -> priceCents;
            case HOURLY -> standardHours.multiply(BigDecimal.valueOf(labourRateCents))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        };
    }
}
