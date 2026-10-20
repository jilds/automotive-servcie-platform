package com.asp.platform.customer.api;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.VehicleDetails;

record VehicleRequest(
        @NotNull UUID customerId,
        @NotBlank String plate,
        @Nullable @Size(min = 17, max = 17) String vin,
        @Nullable String make,
        @Nullable String model,
        @Nullable @Min(1950) @Max(2100) Integer year,
        @Nullable String fuelType,
        @Nullable @PositiveOrZero Integer mileage) {

    VehicleDetails toDetails() {
        return new VehicleDetails(plate, vin, make, model, year, fuelType);
    }
}
