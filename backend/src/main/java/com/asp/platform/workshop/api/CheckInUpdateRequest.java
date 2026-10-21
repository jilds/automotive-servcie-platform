package com.asp.platform.workshop.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.CheckInDetails;

/// Replaces all four check-in fields; a null clears that field.
record CheckInUpdateRequest(
        @Nullable @PositiveOrZero Integer mileageIn,
        @Nullable @Min(0) @Max(8) Integer fuelLevel,
        @Nullable String complaint,
        @Nullable String damageNotes) {

    CheckInDetails toDetails() {
        return new CheckInDetails(mileageIn, fuelLevel, complaint, damageNotes);
    }
}
