package com.asp.platform.stock;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A product the workshop offers in its online shop, with its stock now. Money in cents.
///
/// @param salePriceCents sale price before VAT, per unit
/// @param vatRate        VAT percentage, 0 to 100
/// @param available      on hand minus active reservations; below 0 only after a count correction
/// @param minStock       the product's minimum stock (0 = none)
public record OnlineProduct(UUID id, String sku, String name, @Nullable String brand, String unit,
        long salePriceCents, BigDecimal vatRate, BigDecimal available, BigDecimal minStock) {
}
