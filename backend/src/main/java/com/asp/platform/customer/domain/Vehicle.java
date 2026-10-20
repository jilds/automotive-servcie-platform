package com.asp.platform.customer.domain;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public final class Vehicle {

    private final UUID id;
    private final UUID customerId;
    private final VehicleDetails details;
    private @Nullable Integer mileage;

    public Vehicle(UUID id, UUID customerId, VehicleDetails details, @Nullable Integer mileage) {
        this.id = id;
        this.customerId = customerId;
        this.details = details;
        this.mileage = mileage;
    }

    /// Odometers only go up: a lower reading (a typo, a swapped dashboard) is ignored.
    public void recordMileage(int reading) {
        if (mileage == null || reading > mileage) {
            mileage = reading;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public VehicleDetails details() {
        return details;
    }

    public @Nullable Integer mileage() {
        return mileage;
    }
}
