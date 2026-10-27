package com.asp.platform.shop.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.domain.ShopOrder;

/// `{ "reason": "…", "version": 1 }`: the customer is told the reason; a blank one answers 400
/// `cancel-reason-required`.
record CancelRequest(@Nullable @Size(max = ShopOrder.MAX_CANCEL_REASON) String reason,
        @NotNull @Nullable Long version) {

    long requiredVersion() {
        return Versions.required(version);
    }
}
