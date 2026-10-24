package com.asp.platform.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Plan;
import com.asp.platform.identity.domain.Subscription;
import com.asp.platform.identity.domain.SubscriptionStatus;

/// PUT replaces all three; a null `subscriptionUntil` clears the date. `version` is the workshop version the admin
/// read; a different stored version answers 409 `stale-data`.
record SubscriptionRequest(@NotNull Plan plan, @NotNull SubscriptionStatus status,
        @Nullable LocalDate subscriptionUntil, @NotNull Long version) {

    Subscription toSubscription() {
        return new Subscription(plan, status, subscriptionUntil);
    }
}
