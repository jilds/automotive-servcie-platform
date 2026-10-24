package com.asp.platform.stock.domain;

import com.asp.platform.shared.ConflictException;

/// A purchase order goes DRAFT → ORDERED → RECEIVED, one step at a time. Mapped to 409
/// `illegal-purchase-order-status`.
public class IllegalPurchaseOrderStatusException extends ConflictException {

    public IllegalPurchaseOrderStatusException(PurchaseOrderStatus from, PurchaseOrderStatus to) {
        super("illegal-purchase-order-status", "A purchase order cannot go from " + from + " to " + to);
    }
}
