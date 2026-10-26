package com.asp.platform.identity.application;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.asp.platform.identity.domain.Tenant;

/// Whether a workshop may use ASP now: its status allows it (trial or active) and its subscription has not expired
/// (`subscription_until` plus `app.subscription.grace-days`, read in the workshop's time zone). Sign-in, activation,
/// the per-request access check, the public pages and the scheduled jobs all ask here, so an expired workshop is
/// locked out exactly like a suspended one.
@Component
public class SubscriptionPolicy {

    private final Clock clock;
    private final int graceDays;

    SubscriptionPolicy(Clock clock, SubscriptionProperties properties) {
        this.clock = clock;
        this.graceDays = properties.graceDays();
    }

    public boolean allowsSignIn(Tenant tenant) {
        return tenant.allowsSignIn(clock.instant(), graceDays);
    }

    /// True once the end date plus the grace days has passed, whatever the status; shown in the admin console.
    public boolean expired(Tenant tenant) {
        return tenant.expired(clock.instant(), graceDays);
    }
}
