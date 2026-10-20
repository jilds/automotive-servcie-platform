package com.asp.customers;

import java.util.Optional;
import java.util.UUID;

/** Public API of the customers module, used by the workshop module. */
public interface VehicleDirectory {

    Optional<VehicleSummary> findVehicle(UUID vehicleId);

    /** Records a newer odometer reading, ignoring readings lower than the current one. */
    void recordMileage(UUID vehicleId, int mileage);
}
