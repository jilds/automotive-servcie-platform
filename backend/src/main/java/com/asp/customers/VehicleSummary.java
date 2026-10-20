package com.asp.customers;

import java.util.UUID;

/** What other modules may know about a vehicle. */
public record VehicleSummary(UUID id, UUID customerId, String plate, String vin, String make, String model,
        Integer year, Integer mileage) {
}
