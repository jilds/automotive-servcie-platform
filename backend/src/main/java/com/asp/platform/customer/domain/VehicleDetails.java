package com.asp.platform.customer.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Identity and description of a vehicle as entered at the front desk.
public record VehicleDetails(String plate, @Nullable String vin, @Nullable String make, @Nullable String model,
        @Nullable Integer year, @Nullable String fuelType) {

    public VehicleDetails {
        plate = normalizePlate(plate);
        vin = vin == null || vin.isBlank() ? null : vin.trim().toUpperCase(Locale.ROOT);
    }

    /// Plates are stored upper case without spaces or dashes, so "aa-00 aa" and "AA00AA" are the same car.
    public static String normalizePlate(String plate) {
        String normalized = plate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Plate is required");
        }
        return normalized;
    }
}
