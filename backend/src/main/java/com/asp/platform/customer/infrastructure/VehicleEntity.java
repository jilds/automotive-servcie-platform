package com.asp.platform.customer.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;
import com.asp.platform.customer.domain.VehicleRegistration;

/// `tenant_id` is filled by the database from the connection's workshop (see [CustomerEntity]).
@Entity
@Table(name = "vehicle")
class VehicleEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
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

    @Column(name = "registration_country", nullable = false, length = 2)
    private String registrationCountry;

    /// First day of the registration month; the day carries no meaning.
    @Column(name = "registration_date")
    private @Nullable LocalDate registrationDate;

    /// Optimistic lock: Hibernate adds `where version = ?` to every update and raises it by one. Null until the
    /// entity is first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

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
        apply(vehicle);
    }

    void apply(Vehicle vehicle) {
        VehicleDetails d = vehicle.details();
        this.customerId = vehicle.customerId();
        this.plate = d.plate();
        this.vin = d.vin();
        this.make = d.make();
        this.model = d.model();
        this.year = d.year();
        this.fuelType = d.fuelType();
        this.mileage = vehicle.mileage();
        VehicleRegistration registration = vehicle.registration();
        this.registrationCountry = registration.country();
        YearMonth month = registration.date();
        this.registrationDate = month == null ? null : month.atDay(1);
    }

    Vehicle toDomain() {
        LocalDate date = registrationDate;
        return new Vehicle(id, customerId, new VehicleDetails(plate, vin, make, model, year, fuelType),
                new VehicleRegistration(registrationCountry, date == null ? null : YearMonth.from(date)), mileage,
                version());
    }

    private long version() {
        Long v = version;
        return v == null ? 0 : v;
    }
}
