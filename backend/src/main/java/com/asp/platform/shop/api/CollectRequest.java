package com.asp.platform.shop.api;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/// `{ "paymentMethod": "CARD", "version": 1 }`: how the customer paid at the counter. Checked by the service, so a
/// wrong or missing value answers 400 `invalid-payment-method` on `paymentMethod`.
record CollectRequest(@Nullable String paymentMethod, @NotNull @Nullable Long version) {

    long requiredVersion() {
        return Versions.required(version);
    }
}
