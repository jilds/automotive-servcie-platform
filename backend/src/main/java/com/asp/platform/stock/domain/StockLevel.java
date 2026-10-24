package com.asp.platform.stock.domain;

import java.math.BigDecimal;

/// How much of a product the workshop has and how much of it is promised.
///
/// @param onHand   physically in the workshop
/// @param reserved held by active reservations (open work orders, later shop orders)
public record StockLevel(BigDecimal onHand, BigDecimal reserved) {

    /// What can still be promised: on hand minus reserved. Negative only when a count correction took away parts
    /// that were already reserved.
    public BigDecimal available() {
        return onHand.subtract(reserved);
    }

    /// Low stock: less available than the product's minimum. A minimum of 0 never counts as low.
    public boolean isLow(BigDecimal minStock) {
        return minStock.signum() > 0 && available().compareTo(minStock) < 0;
    }

    /// @throws InsufficientStockException when less than `quantity` is available
    public void requireAvailable(BigDecimal quantity) {
        if (available().compareTo(quantity) < 0) {
            throw new InsufficientStockException();
        }
    }
}
