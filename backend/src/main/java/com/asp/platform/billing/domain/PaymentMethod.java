package com.asp.platform.billing.domain;

public enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    MULTIBANCO,
    MBWAY;

    /// Methods that go through the payment provider; the others are recorded by staff and are PAID at once.
    public boolean viaProvider() {
        return this == MULTIBANCO || this == MBWAY;
    }
}
