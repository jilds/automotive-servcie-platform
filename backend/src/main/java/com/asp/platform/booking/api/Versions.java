package com.asp.platform.booking.api;

import org.jspecify.annotations.Nullable;

/// The `version` of a request, which Bean Validation has already checked.
final class Versions {

    private Versions() {
    }

    static long required(@Nullable Long version) {
        if (version == null) {
            throw new IllegalStateException("version is validated by @NotNull");
        }
        return version;
    }
}
