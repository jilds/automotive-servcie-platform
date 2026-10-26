package com.asp.platform.identity.domain;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/// A workshop's subscription as the platform owner set it up.
///
/// @param plan   the plan label
/// @param status whether the workshop may use ASP
/// @param until  end of the trial or of the paid period, when known; after it and the grace days the workshop is
///               locked out like a suspended one
public record Subscription(Plan plan, SubscriptionStatus status, @Nullable LocalDate until) {

    /// True while the workshop's users may sign in on `today` (the workshop's own calendar day): the status allows
    /// it and the subscription has not expired.
    public boolean allowsSignIn(LocalDate today, int graceDays) {
        return status.allowsSignIn() && !expired(today, graceDays);
    }

    /// True once `until` plus the grace days lies before `today`. A subscription without an end date never expires.
    public boolean expired(LocalDate today, int graceDays) {
        return until != null && today.isAfter(until.plusDays(graceDays));
    }
}
