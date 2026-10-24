package com.asp.platform.identity.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Subscription;

/// A workshop the platform admin registers, with its first owner.
///
/// @param slug branded login address; null to derive it from the name
public record ProvisionWorkshopCommand(String name, @Nullable String nif, String country, String timezone,
        Subscription subscription, @Nullable String slug, String ownerName, String ownerEmail) {
}
