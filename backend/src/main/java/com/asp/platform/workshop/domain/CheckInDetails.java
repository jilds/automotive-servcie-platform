package com.asp.platform.workshop.domain;

import org.jspecify.annotations.Nullable;

/// What the front desk records when the car arrives.
///
/// @param fuelLevel fuel at check-in, from 0 (empty) to 8 (full) eighths
public record CheckInDetails(@Nullable Integer mileageIn, @Nullable Integer fuelLevel, @Nullable String complaint,
        @Nullable String damageNotes) {

    private static final int FULL_TANK_EIGHTHS = 8;

    public CheckInDetails {
        if (mileageIn != null && mileageIn < 0) {
            throw new IllegalArgumentException("Mileage cannot be negative");
        }
        if (fuelLevel != null && (fuelLevel < 0 || fuelLevel > FULL_TANK_EIGHTHS)) {
            throw new IllegalArgumentException("Fuel level must be between 0 and 8");
        }
    }
}
