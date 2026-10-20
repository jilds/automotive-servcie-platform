package com.asp.customers.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.asp.shared.tenancy.TenantScopedEntity;

@Entity
@Table(name = "vehicle")
class Vehicle extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Stored normalized: upper case, no spaces or dashes (AA-00-AA becomes AA00AA). */
    @Column(nullable = false)
    private String plate;

    private String vin;
    private String make;
    private String model;

    @Column(name = "model_year")
    private Integer year;

    @Column(name = "fuel_type")
    private String fuelType;

    private Integer mileage;

    protected Vehicle() {
    }

    Vehicle(UUID customerId, String plate, String vin, String make, String model, Integer year, String fuelType,
            Integer mileage) {
        this.customerId = customerId;
        this.plate = normalizePlate(plate);
        this.vin = vin == null ? null : vin.trim().toUpperCase();
        this.make = make;
        this.model = model;
        this.year = year;
        this.fuelType = fuelType;
        this.mileage = mileage;
    }

    static String normalizePlate(String plate) {
        return plate.replaceAll("[\\s-]", "").toUpperCase();
    }

    void recordMileage(int reading) {
        if (mileage == null || reading > mileage) {
            mileage = reading;
        }
    }

    UUID getId() { return id; }
    UUID getCustomerId() { return customerId; }
    String getPlate() { return plate; }
    String getVin() { return vin; }
    String getMake() { return make; }
    String getModel() { return model; }
    Integer getYear() { return year; }
    String getFuelType() { return fuelType; }
    Integer getMileage() { return mileage; }
}
