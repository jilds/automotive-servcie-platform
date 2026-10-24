package com.asp.platform.identity.domain;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/// A workshop's subscription as the platform owner set it up.
///
/// @param plan   the plan label
/// @param status whether the workshop may use ASP
/// @param until  end of the trial or of the paid period, when known; shown to the platform admin, not enforced
public record Subscription(Plan plan, SubscriptionStatus status, @Nullable LocalDate until) {
}
