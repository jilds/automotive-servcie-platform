package com.asp.platform.shop.domain;

/// Who closed an order without collection: the customer on the status link, the front desk, or the hourly expiry.
public enum CancelledBy {
    CUSTOMER,
    STAFF,
    EXPIRY
}
