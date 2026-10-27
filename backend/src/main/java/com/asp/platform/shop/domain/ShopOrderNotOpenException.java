package com.asp.platform.shop.domain;

import com.asp.platform.shared.ConflictException;

/// A status move the order's current status does not allow, such as collecting a cancelled order or a customer
/// cancelling an order that is ready. Mapped to 409 `shop-order-not-open`.
public class ShopOrderNotOpenException extends ConflictException {

    public static final String CODE = "shop-order-not-open";

    public ShopOrderNotOpenException() {
        super(CODE, "This order can no longer be changed this way");
    }
}
