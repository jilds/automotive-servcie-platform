package com.asp.platform.shop.domain;

import com.asp.platform.shared.InvalidInputException;

/// Not one of CASH, CARD, MULTIBANCO, MBWAY. Mapped to 400 `invalid-payment-method` on `paymentMethod`.
public class InvalidPaymentMethodException extends InvalidInputException {

    public static final String CODE = "invalid-payment-method";

    public InvalidPaymentMethodException() {
        super(CODE, "paymentMethod", "Payment method must be CASH, CARD, MULTIBANCO or MBWAY");
    }
}
