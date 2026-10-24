package com.asp.platform.stock.domain;

import com.asp.platform.shared.ConflictException;

/// Only a draft purchase order can be changed. Mapped to 409 `purchase-order-locked`.
public class PurchaseOrderLockedException extends ConflictException {

    public PurchaseOrderLockedException() {
        super("purchase-order-locked", "This purchase order was already sent and can no longer be changed");
    }
}
