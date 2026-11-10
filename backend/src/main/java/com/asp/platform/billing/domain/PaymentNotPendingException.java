package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// Cancel, refresh or simulate of a payment that is not PENDING. 409.
public class PaymentNotPendingException extends ConflictException {

    public PaymentNotPendingException() {
        super("payment-not-pending", "The payment is not pending");
    }
}
