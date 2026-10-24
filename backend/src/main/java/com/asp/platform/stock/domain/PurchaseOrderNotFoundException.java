package com.asp.platform.stock.domain;

import com.asp.platform.shared.NotFoundException;

/// Mapped to 404 `purchase-order-not-found`.
public class PurchaseOrderNotFoundException extends NotFoundException {

    public PurchaseOrderNotFoundException() {
        super("purchase-order-not-found", "Purchase order not found");
    }
}
