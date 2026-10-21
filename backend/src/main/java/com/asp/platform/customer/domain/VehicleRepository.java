package com.asp.platform.customer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface VehicleRepository {

    /// Writes the vehicle at once, so a plate taken by a concurrent save is caught here.
    ///
    /// @throws DuplicatePlateException when another vehicle of the workshop already has the plate
    Vehicle save(Vehicle vehicle);

    Optional<Vehicle> findById(UUID id);

    /// One query for all ids; ids not found are left out.
    List<Vehicle> findAllById(Collection<UUID> ids);

    Optional<Vehicle> findByPlate(String normalizedPlate);

    boolean existsByPlate(String normalizedPlate);

    List<Vehicle> findByCustomer(UUID customerId);
}
