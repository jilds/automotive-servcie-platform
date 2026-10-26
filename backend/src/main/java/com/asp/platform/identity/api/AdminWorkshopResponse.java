package com.asp.platform.identity.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.AddOns;
import com.asp.platform.identity.domain.Plan;
import com.asp.platform.identity.domain.SubscriptionStatus;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.WorkshopOverview;

/// A workshop as the platform admin sees it: identity, subscription and add-ons, never its business data.
///
/// @param ownerEmail the first owner's email, or null
/// @param users      number of users, pending ones included
/// @param addOns     online booking and online shop, each on or off
/// @param version    sent back with the next subscription, add-ons or branding change (optimistic locking)
record AdminWorkshopResponse(UUID id, String name, @Nullable String nif, String country, String timezone,
        String slug, @Nullable String displayName, Plan plan, SubscriptionStatus status,
        @Nullable LocalDate subscriptionUntil, Instant createdAt, @Nullable String ownerEmail, long users,
        AddOns addOns, boolean expired, long version) {

    /// @param expired true once `subscriptionUntil` plus the grace days has passed: the workshop is locked out like a
    ///                suspended one until the admin moves the date
    static AdminWorkshopResponse of(WorkshopOverview o, boolean expired) {
        Tenant t = o.tenant();
        return new AdminWorkshopResponse(t.id(), t.settings().name(), t.settings().nif(), t.settings().country(),
                t.settings().timezone(), t.slug().value(), t.branding().displayName(), t.subscription().plan(),
                t.subscription().status(), t.subscription().until(), t.createdAt(), o.ownerEmail(), o.users(),
                t.addOns(), expired, t.version());
    }
}
