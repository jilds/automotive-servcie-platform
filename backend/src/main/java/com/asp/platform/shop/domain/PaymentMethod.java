package com.asp.platform.shop.domain;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

/// How the customer paid at the counter. Only a label: no payment is processed (invoicing comes with step 07).
public enum PaymentMethod {
    CASH,
    CARD,
    MULTIBANCO,
    MBWAY;

    /// @throws InvalidPaymentMethodException for null or anything but the four names
    public static PaymentMethod parse(@Nullable String value) {
        return Arrays.stream(values()).filter(m -> m.name().equals(value)).findFirst()
                .orElseThrow(InvalidPaymentMethodException::new);
    }
}
