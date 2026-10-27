package com.asp.platform.shop.domain;

import java.util.UUID;

import com.asp.platform.shared.ConflictException;

/// One product of a new order cannot be sold: the answer names it (`productId`), so the page can mark the line.
/// Nothing of the order is stored. Mapped to 409 with the code and `productId`.
public abstract class OrderProductException extends ConflictException {

    private final UUID productId;

    protected OrderProductException(String code, String message, UUID productId) {
        super(code, message);
        this.productId = productId;
    }

    public UUID productId() {
        return productId;
    }
}
