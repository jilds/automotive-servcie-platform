package com.asp.platform.customer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// Public API of the customer module, used by other modules (the workshop opens work orders on vehicles).
public interface CustomerManagement {

    Optional<VehicleSummary> findVehicle(UUID vehicleId);

    /// The vehicles with these ids, in one query, keyed by id. Ids not found in the workshop are left out.
    Map<UUID, VehicleSummary> findVehicles(Collection<UUID> vehicleIds);

    /// Records a newer odometer reading, ignoring readings lower than the current one.
    void recordMileage(UUID vehicleId, int mileage);
}
