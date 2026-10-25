package com.asp.platform.workshop.api;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.CheckInDetails;

record CheckInRequest(
        @NotNull UUID vehicleId,
        @Nullable @PositiveOrZero Integer mileageIn,
        @Nullable @Min(0) @Max(8) Integer fuelLevel,
        @Nullable String complaint,
        @Nullable String damageNotes,
        @Nullable UUID assignedMechanicId,
        @Nullable UUID bookingId) {

    CheckInDetails toDetails() {
        return new CheckInDetails(mileageIn, fuelLevel, complaint, damageNotes);
    }
}
