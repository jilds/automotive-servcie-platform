package com.asp.platform.shop.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/// One product of an order, as the stock module sold it when the order was placed: never changed afterwards.
///
/// @param unitPriceCents sale price before VAT, per unit, in cents
/// @param vatRate        VAT percentage, 0 to 100
public record ShopOrderLine(UUID id, UUID productId, String sku, String name, String unit, int quantity,
        long unitPriceCents, BigDecimal vatRate) {

    public static final int MAX_QUANTITY = 20;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ShopOrderLine {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity must be 1 to " + MAX_QUANTITY);
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (vatRate.signum() < 0 || vatRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
    }

    /// `round_half_up(unitPriceCents × (100 + vatRate) / 100)`.
    public long unitPriceWithVatCents() {
        return withVat(unitPriceCents, vatRate);
    }

    /// `quantity × unitPriceWithVatCents`.
    public long lineTotalCents() {
        return quantity * unitPriceWithVatCents();
    }

    /// A price before VAT plus VAT, rounded half up to whole cents.
    public static long withVat(long cents, BigDecimal vatRate) {
        return BigDecimal.valueOf(cents).multiply(HUNDRED.add(vatRate))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
    }
}
