package com.asp.platform.billing.domain;

/// PENDING: stored, the provider has not answered yet (or the answer was lost). ISSUED: the provider gave it a number.
/// FAILED: the provider refused it. VOIDED: an issued invoice annulled at the provider. DISCARDED: a failed document
/// given up on.
public enum InvoiceStatus {
    PENDING,
    ISSUED,
    FAILED,
    VOIDED,
    DISCARDED;

    /// Documents that hold the work order (or the invoice, for a credit note): one at a time.
    public boolean isActive() {
        return this == PENDING || this == ISSUED || this == FAILED;
    }

    /// Documents that still wait for the provider.
    public boolean awaitsProvider() {
        return this == PENDING || this == FAILED;
    }
}
