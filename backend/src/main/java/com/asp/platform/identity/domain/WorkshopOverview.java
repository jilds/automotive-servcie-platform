package com.asp.platform.identity.domain;

import org.jspecify.annotations.Nullable;

/// One row of the platform admin's workshop list.
///
/// @param ownerEmail the first owner's email, or null when the workshop has no owner yet
/// @param users      every user of the workshop, active, inactive or pending
public record WorkshopOverview(Tenant tenant, @Nullable String ownerEmail, long users) {
}
