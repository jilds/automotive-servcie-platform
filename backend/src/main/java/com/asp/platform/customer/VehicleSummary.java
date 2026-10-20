package com.asp.platform.customer;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// What other modules may know about a vehicle. No personal data of the owner.
public record VehicleSummary(UUID id, UUID customerId, String plate, @Nullable String vin, @Nullable String make,
        @Nullable String model, @Nullable Integer year, @Nullable Integer mileage) {

    /// "Renault Clio", or an empty string when neither is known.
    public String label() {
        return ((make == null ? "" : make) + " " + (model == null ? "" : model)).trim();
    }
}
