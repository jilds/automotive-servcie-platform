package com.asp.platform.shop.domain;

/// PLACED → READY → COLLECTED; PLACED or READY → CANCELLED or EXPIRED. Only PLACED and READY orders hold stock.
public enum ShopOrderStatus {
    PLACED,
    READY,
    COLLECTED,
    CANCELLED,
    EXPIRED;

    /// True while the order holds its reservations and can still change.
    public boolean isOpen() {
        return this == PLACED || this == READY;
    }
}
