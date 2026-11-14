package com.asp.platform.billing.domain;

public enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    MULTIBANCO,
    /// Sale in instalments approved on the card terminal (Parcela Já): recorded by staff, PAID at once.
    INSTALMENTS,
    MBWAY;

    /// Methods that go through the payment provider; the others are recorded by staff and are PAID at once.
    public boolean viaProvider() {
        return this == MULTIBANCO || this == MBWAY;
    }
}
