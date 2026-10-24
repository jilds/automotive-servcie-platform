package com.asp.platform.stock.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/// One product on a purchase order. Money in cents.
///
/// @param unitCostCents what the supplier charges per unit, before VAT
public record PurchaseOrderLine(UUID id, UUID productId, BigDecimal quantity, long unitCostCents) {

    public PurchaseOrderLine {
        Quantities.positive(quantity, "Quantity");
        if (unitCostCents < 0) {
            throw new IllegalArgumentException("Unit cost cannot be negative");
        }
    }

    /// Quantity times unit cost, rounded half up to the cent.
    public long totalCents() {
        return quantity.multiply(BigDecimal.valueOf(unitCostCents)).setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
