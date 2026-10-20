package com.asp.platform.customer.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;

record VehicleResponse(UUID id, UUID customerId, String plate, @Nullable String vin, @Nullable String make,
        @Nullable String model, @Nullable Integer year, @Nullable String fuelType, @Nullable Integer mileage) {

    static VehicleResponse of(Vehicle vehicle) {
        VehicleDetails d = vehicle.details();
        return new VehicleResponse(vehicle.id(), vehicle.customerId(), d.plate(), d.vin(), d.make(), d.model(),
                d.year(), d.fuelType(), vehicle.mileage());
    }
}
