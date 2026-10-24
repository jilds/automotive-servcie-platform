package com.asp.platform.workshop.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A labor or part line. Money is kept in integer cents and rounded half up, line by line.
///
/// @param serviceItemId the catalogue service the line was added from, or null for a line typed by hand
/// @param productId     the stock product the line reserves, or null when it does not come from stock
public record WorkOrderLine(UUID id, int position, LineType type, String description, BigDecimal quantity,
        long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate, @Nullable UUID serviceItemId,
        @Nullable UUID productId) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public WorkOrderLine {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
        requirePercent(discountPercent, "Discount");
        requirePercent(vatRate, "VAT rate");
    }

    static WorkOrderLine from(UUID id, int position, NewLine line) {
        BigDecimal discount = line.discountPercent() == null ? BigDecimal.ZERO : line.discountPercent();
        return new WorkOrderLine(id, position, line.type(), line.description(), line.quantity(),
                line.unitPriceCents(), discount, line.vatRate(), line.serviceItemId(), line.productId());
    }

    public long netCents() {
        BigDecimal gross = quantity.multiply(BigDecimal.valueOf(unitPriceCents));
        BigDecimal factor = HUNDRED.subtract(discountPercent).divide(HUNDRED);
        return gross.multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public long vatCents() {
        return BigDecimal.valueOf(netCents()).multiply(vatRate).divide(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static void requirePercent(BigDecimal value, String name) {
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }
}
