package com.asp.platform.booking.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.domain.Booking;

/// `{ "reason": "…", "version": 1 }`: the customer sees the reason on the status link; a blank one answers 400
/// `decline-reason-required`.
record DeclineRequest(@Nullable @Size(max = Booking.MAX_DECLINE_REASON) String reason,
        @NotNull @Nullable Long version) {

    long requiredVersion() {
        return Versions.required(version);
    }
}
