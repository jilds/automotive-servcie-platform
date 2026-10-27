package com.asp.platform.shop.domain;

import com.asp.platform.shared.ConflictException;

/// The workshop already holds as many open online orders (PLACED or READY) as `app.shop.max-open-orders` allows.
/// Orders are paid only at the counter, so the cap keeps anonymous orders from holding all the stock. Mapped to
/// 409 `shop-busy`.
public class ShopBusyException extends ConflictException {

    public static final String CODE = "shop-busy";

    public ShopBusyException() {
        super(CODE, "The workshop cannot take more online orders right now");
    }
}
