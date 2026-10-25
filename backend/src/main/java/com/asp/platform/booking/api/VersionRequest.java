package com.asp.platform.booking.api;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/// `{ "version": 1 }`: the version of the booking the front desk read.
record VersionRequest(@NotNull @Nullable Long version) {

    long requiredVersion() {
        return Versions.required(version);
    }
}
