package com.asp.platform.booking.domain;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/// A whole day without bookings, such as a public holiday, in the workshop's calendar.
public record Closure(LocalDate day, @Nullable String reason) {

    public static final int MAX_REASON = 120;

    public Closure {
        reason = reason == null || reason.isBlank() ? null : reason.strip();
        if (reason != null && reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("Reason is longer than " + MAX_REASON + " characters");
        }
    }
}
