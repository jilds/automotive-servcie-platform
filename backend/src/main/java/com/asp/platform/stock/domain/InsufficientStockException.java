package com.asp.platform.stock.domain;

import com.asp.platform.shared.ConflictException;

/// Not enough of the product is available for a reservation, or a movement would leave less than nothing on hand.
/// Mapped to 409 `insufficient-stock`.
public class InsufficientStockException extends ConflictException {

    public InsufficientStockException() {
        super("insufficient-stock", "Not enough of this product in stock");
    }
}
