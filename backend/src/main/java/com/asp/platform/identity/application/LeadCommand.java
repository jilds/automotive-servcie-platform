package com.asp.platform.identity.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Lead;

/// A demo request from the landing site, already validated for shape; the privacy notice was accepted.
public record LeadCommand(String name, String workshopName, @Nullable String phone, @Nullable String email,
        @Nullable String district, Lead.VehicleType vehicleType, @Nullable String message,
        boolean marketingConsent) {
}
