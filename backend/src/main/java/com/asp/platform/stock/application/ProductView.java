package com.asp.platform.stock.application;

import java.math.BigDecimal;

import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.StockLevel;

/// A product with its stock: on hand, reserved and available.
public record ProductView(Product product, StockLevel level) {

    public BigDecimal available() {
        return level.available();
    }

    /// Less available than the product's minimum stock.
    public boolean low() {
        return level.isLow(product.details().minStock());
    }
}
