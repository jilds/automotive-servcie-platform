package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.asp.platform.shared.StaleDataException;

/// A workshop subscribed to ASP.
///
/// @param version how many times the stored row has changed (settings, branding or subscription); edits name the
///                version they read
public record Tenant(UUID id, WorkshopSettings settings, Subscription subscription, Slug slug, Branding branding,
        Instant createdAt, long version) {

    /// A workshop not saved yet.
    public Tenant(UUID id, WorkshopSettings settings, Subscription subscription, Slug slug, Branding branding,
            Instant createdAt) {
        this(id, settings, subscription, slug, branding, createdAt, 0);
    }

    /// A workshop the platform admin registers; it starts with the ASP default theme.
    public static Tenant provision(UUID id, WorkshopSettings settings, Subscription subscription, Slug slug,
            Instant now) {
        return new Tenant(id, settings, subscription, slug, Branding.none(), now);
    }

    public Tenant withSettings(WorkshopSettings newSettings) {
        return new Tenant(id, newSettings, subscription, slug, branding, createdAt, version);
    }

    public Tenant withSubscription(Subscription newSubscription) {
        return new Tenant(id, settings, newSubscription, slug, branding, createdAt, version);
    }

    public Tenant withBranding(Slug newSlug, Branding newBranding) {
        return new Tenant(id, settings, subscription, newSlug, newBranding, createdAt, version);
    }

    /// @param expectedVersion the version the editor read
    /// @throws StaleDataException when someone else saved the workshop since
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    public boolean allowsSignIn() {
        return subscription.status().allowsSignIn();
    }

    /// The name to show: the display name when set, else the workshop's trading name.
    public String shownName() {
        String displayName = branding.displayName();
        return displayName != null ? displayName : settings.name();
    }
}
