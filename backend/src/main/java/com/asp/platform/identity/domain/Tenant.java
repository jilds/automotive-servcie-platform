package com.asp.platform.identity.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.asp.platform.shared.StaleDataException;

/// A workshop subscribed to ASP.
///
/// @param addOns  the add-ons the platform admin switched on (online booking, online shop)
/// @param version how many times the stored row has changed (settings, branding, subscription or add-ons); edits
///                name the version they read
public record Tenant(UUID id, WorkshopSettings settings, Subscription subscription, Slug slug, Branding branding,
        AddOns addOns, Instant createdAt, long version) {

    /// A workshop not saved yet.
    public Tenant(UUID id, WorkshopSettings settings, Subscription subscription, Slug slug, Branding branding,
            AddOns addOns, Instant createdAt) {
        this(id, settings, subscription, slug, branding, addOns, createdAt, 0);
    }

    /// A workshop the platform admin registers; it starts with the ASP default theme and no add-ons.
    public static Tenant provision(UUID id, WorkshopSettings settings, Subscription subscription, Slug slug,
            Instant now) {
        return new Tenant(id, settings, subscription, slug, Branding.none(), AddOns.none(), now);
    }

    public Tenant withSettings(WorkshopSettings newSettings) {
        return new Tenant(id, newSettings, subscription, slug, branding, addOns, createdAt, version);
    }

    public Tenant withSubscription(Subscription newSubscription) {
        return new Tenant(id, settings, newSubscription, slug, branding, addOns, createdAt, version);
    }

    public Tenant withBranding(Slug newSlug, Branding newBranding) {
        return new Tenant(id, settings, subscription, newSlug, newBranding, addOns, createdAt, version);
    }

    public Tenant withAddOns(AddOns newAddOns) {
        return new Tenant(id, settings, subscription, slug, branding, newAddOns, createdAt, version);
    }

    /// @param expectedVersion the version the editor read
    /// @throws StaleDataException when someone else saved the workshop since
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// True while the workshop's users may sign in at `now`: see [Subscription#allowsSignIn]. The day is read in the
    /// workshop's time zone.
    public boolean allowsSignIn(Instant now, int graceDays) {
        return subscription.allowsSignIn(today(now), graceDays);
    }

    /// True once the subscription's end date plus the grace days has passed: see [Subscription#expired].
    public boolean expired(Instant now, int graceDays) {
        return subscription.expired(today(now), graceDays);
    }

    private LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, settings.zone());
    }

    /// The name to show: the display name when set, else the workshop's trading name.
    public String shownName() {
        String displayName = branding.displayName();
        return displayName != null ? displayName : settings.name();
    }
}
