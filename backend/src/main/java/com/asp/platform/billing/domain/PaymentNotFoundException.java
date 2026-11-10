package com.asp.platform.billing.domain;

import com.asp.platform.shared.NotFoundException;

/// Unknown payment or one of another workshop. 404 payment-not-found.
public class PaymentNotFoundException extends NotFoundException {

    public PaymentNotFoundException() {
        super("payment-not-found", "Payment not found");
    }
}
