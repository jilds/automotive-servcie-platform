package com.asp.platform.stock.domain;

import com.asp.platform.shared.NotFoundException;

/// The workshop has no such product (another workshop's product reads the same). Mapped to 404 `product-not-found`.
public class ProductNotFoundException extends NotFoundException {

    public ProductNotFoundException() {
        super("product-not-found", "Product not found");
    }
}
