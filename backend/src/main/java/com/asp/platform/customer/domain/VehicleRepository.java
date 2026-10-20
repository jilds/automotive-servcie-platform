package com.asp.platform.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface VehicleRepository {

    Vehicle save(Vehicle vehicle);

    Optional<Vehicle> findById(UUID id);

    Optional<Vehicle> findByPlate(String normalizedPlate);

    boolean existsByPlate(String normalizedPlate);

    List<Vehicle> findByCustomer(UUID customerId);
}
