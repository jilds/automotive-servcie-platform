package com.asp.platform.customer.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Identity and description of a vehicle as entered at the front desk.
public record VehicleDetails(String plate, @Nullable String vin, @Nullable String make, @Nullable String model,
        @Nullable Integer year, @Nullable String fuelType) {

    /// A vehicle identification number has 17 characters.
    public static final int VIN_LENGTH = 17;

    public VehicleDetails {
        plate = normalizePlate(plate);
        vin = vin == null || vin.isBlank() ? null : normalizeVin(vin);
    }

    /// Plates are stored upper case without spaces or dashes, so "aa-00 aa" and "AA00AA" are the same car.
    public static String normalizePlate(String plate) {
        String normalized = plate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Plate is required");
        }
        return normalized;
    }

    /// VINs are stored upper case without spaces, so a VIN typed in groups or in lower case still matches.
    public static String normalizeVin(String vin) {
        return vin.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
