package com.asp.platform.stock;

/// What holds a reservation of stock.
public enum ReservationSource {
    /// A part line of a work order.
    WORK_ORDER,
    /// A line of an online shop order (later release).
    SHOP_ORDER
}
