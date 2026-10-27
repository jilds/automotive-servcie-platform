package com.asp.platform.shop.domain;

import com.asp.platform.shared.NotFoundException;

/// No such order in the workshop, or an unknown status link. Mapped to 404 `shop-order-not-found`.
public class ShopOrderNotFoundException extends NotFoundException {

    public static final String CODE = "shop-order-not-found";

    public ShopOrderNotFoundException() {
        super(CODE, "Order not found");
    }
}
