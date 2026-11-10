package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// The payment is more than what is left of the invoice (paid and pending counted). 409 with leftCents.
public class PaymentExceedsBalanceException extends ConflictException {

    private final long leftCents;

    public PaymentExceedsBalanceException(long leftCents) {
        super("payment-exceeds-balance", "The amount is more than what is left to pay");
        this.leftCents = leftCents;
    }

    public long leftCents() {
        return leftCents;
    }
}
