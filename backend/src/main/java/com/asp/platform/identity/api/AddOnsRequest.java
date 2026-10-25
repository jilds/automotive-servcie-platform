package com.asp.platform.identity.api;

import jakarta.validation.constraints.NotNull;

import com.asp.platform.identity.domain.AddOns;

/// PUT replaces both flags. `version` is the workshop version the admin read; a different stored version answers
/// 409 `stale-data`.
record AddOnsRequest(@NotNull Boolean booking, @NotNull Boolean shop, @NotNull Long version) {

    AddOns toAddOns() {
        return new AddOns(booking, shop);
    }
}
