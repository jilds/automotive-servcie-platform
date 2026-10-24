package com.asp.platform.stock.domain;

/// A purchase order is prepared (DRAFT), sent to the supplier (ORDERED) and, when the parts arrive, RECEIVED.
public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    RECEIVED
}
