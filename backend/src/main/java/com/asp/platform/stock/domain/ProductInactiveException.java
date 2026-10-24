package com.asp.platform.stock.domain;

import com.asp.platform.shared.ConflictException;

/// An inactive product cannot be reserved or ordered. Mapped to 409 `product-inactive`.
public class ProductInactiveException extends ConflictException {

    public ProductInactiveException() {
        super("product-inactive", "This product is no longer sold");
    }
}
