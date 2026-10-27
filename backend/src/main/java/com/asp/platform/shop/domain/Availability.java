package com.asp.platform.shop.domain;

import java.math.BigDecimal;

/// What the public page says about stock: never a number.
public enum Availability {
    IN_STOCK,
    /// Available at or below the product's minimum stock, or at most [#FEW] units when it has no minimum.
    LAST_UNITS,
    /// Nothing available: the product is shown but cannot be ordered.
    OUT;

    static final BigDecimal FEW = BigDecimal.valueOf(3);

    public static Availability of(BigDecimal available, BigDecimal minStock) {
        if (available.signum() <= 0) {
            return OUT;
        }
        BigDecimal threshold = minStock.signum() > 0 ? minStock : FEW;
        return available.compareTo(threshold) <= 0 ? LAST_UNITS : IN_STOCK;
    }
}
