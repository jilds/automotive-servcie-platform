package com.asp.platform.identity.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Lead;

/// A demo request as the platform admin sees it. `version` goes back with a status change.
record LeadResponse(UUID id, String name, String workshopName, @Nullable String phone, @Nullable String email,
        @Nullable String district, Lead.VehicleType vehicleType, @Nullable String message, boolean marketingConsent,
        Lead.Status status, Instant createdAt, @Nullable Instant statusChangedAt, long version) {

    static LeadResponse of(Lead l) {
        return new LeadResponse(l.id(), l.name(), l.workshopName(), l.phone(), l.email(), l.district(),
                l.vehicleType(), l.message(), l.marketingConsent(), l.status(), l.createdAt(), l.statusChangedAt(),
                l.version());
    }
}
