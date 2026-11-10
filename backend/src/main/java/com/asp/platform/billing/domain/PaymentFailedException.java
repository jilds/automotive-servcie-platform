package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// The payment provider refused to create the payment (for example MB WAY is not active on that number). Nothing was
/// stored. 409 payment-failed with the provider machine code as reason (never the provider text).
public class PaymentFailedException extends ConflictException {

    private final String reason;

    public PaymentFailedException(String reason) {
        super("payment-failed", "The payment provider refused the payment");
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
