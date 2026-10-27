package com.asp.platform.shop.domain;

import java.util.UUID;

/// The product is unknown, inactive or not listed online. Mapped to 409 `product-not-listed` with `productId`.
public class ProductNotListedException extends OrderProductException {

    public static final String CODE = "product-not-listed";

    public ProductNotListedException(UUID productId) {
        super(CODE, "This product is not sold online", productId);
    }
}
