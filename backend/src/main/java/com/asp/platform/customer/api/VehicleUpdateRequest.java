package com.asp.platform.customer.api;

import java.time.YearMonth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.VehicleDetails;
import com.asp.platform.shared.IsoCountry;

/// The same fields as [VehicleRequest] without the owner, which changes through its own endpoint.
/// A null mileage keeps the current reading; a null registration country keeps the current country.
record VehicleUpdateRequest(
        @NotBlank String plate,
        @Nullable @Size(min = 17, max = 17) String vin,
        @Nullable String make,
        @Nullable String model,
        @Nullable @Min(1950) @Max(2100) Integer year,
        @Nullable String fuelType,
        @Nullable @PositiveOrZero Integer mileage,
        @Nullable @IsoCountry String registrationCountry,
        @Nullable @RegistrationMonth YearMonth registrationDate) {

    VehicleDetails toDetails() {
        return new VehicleDetails(plate, vin, make, model, year, fuelType);
    }
}
