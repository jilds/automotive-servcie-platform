package com.asp.platform.customer.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;

/// `tenant_id` is filled by the database from the connection's workshop (see [CustomerEntity]).
@Entity
@Table(name = "vehicle")
class VehicleEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String plate;

    private @Nullable String vin;
    private @Nullable String make;
    private @Nullable String model;

    @Column(name = "model_year")
    private @Nullable Integer year;

    @Column(name = "fuel_type")
    private @Nullable String fuelType;

    private @Nullable Integer mileage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected VehicleEntity() {
    }

    VehicleEntity(Vehicle vehicle) {
        this.id = vehicle.id();
        this.customerId = vehicle.customerId();
        apply(vehicle);
    }

    void apply(Vehicle vehicle) {
        VehicleDetails d = vehicle.details();
        this.plate = d.plate();
        this.vin = d.vin();
        this.make = d.make();
        this.model = d.model();
        this.year = d.year();
        this.fuelType = d.fuelType();
        this.mileage = vehicle.mileage();
    }

    Vehicle toDomain() {
        return new Vehicle(id, customerId, new VehicleDetails(plate, vin, make, model, year, fuelType), mileage);
    }
}
