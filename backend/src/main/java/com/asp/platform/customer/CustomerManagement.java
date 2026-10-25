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

    /// The vehicles of the workshop with these plates, in one query, keyed by the stored (normalised) plate. Plates
    /// are normalised first ([#normalizePlate]); plates no vehicle has are left out.
    Map<String, VehicleSummary> findVehiclesByPlates(Collection<String> plates);

    /// A plate as vehicles store it: upper case, no spaces or dashes ("aa-00 aa" becomes "AA00AA").
    ///
    /// @throws IllegalArgumentException when nothing is left of it
    String normalizePlate(String plate);

    /// Records a newer odometer reading, ignoring readings lower than the current one.
    void recordMileage(UUID vehicleId, int mileage);
}
