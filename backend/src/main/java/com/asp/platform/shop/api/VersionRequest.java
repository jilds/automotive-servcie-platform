package com.asp.platform.shop.api;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/// `{ "version": 1 }`: the version of the order the front desk read.
record VersionRequest(@NotNull @Nullable Long version) {

    long requiredVersion() {
        return Versions.required(version);
    }
}
