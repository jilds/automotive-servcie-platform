package com.asp.platform.shop.domain;

import java.util.UUID;

/// Less of the product is available than the line asks for. Mapped to 409 `insufficient-stock` with `productId`
/// (the same code as for work orders, which carry no product id).
public class OutOfStockException extends OrderProductException {

    public static final String CODE = "insufficient-stock";

    public OutOfStockException(UUID productId) {
        super(CODE, "Not enough of this product in stock", productId);
    }
}
