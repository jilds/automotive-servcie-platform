package com.asp.platform.billing.domain;

/// What a payment provider says about a payment.
public enum ProviderPaymentState {
    PENDING,
    PAID,
    FAILED,
    EXPIRED
}
