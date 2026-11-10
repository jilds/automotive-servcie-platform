package com.asp.platform.billing.domain;

/// How much of an issued invoice is settled.
public enum PaymentProgress {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    CREDITED
}
