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

    /// The name and e-mail of the customer who owns the vehicle, for a message to them; empty when the vehicle is
    /// not found in the workshop.
    Optional<CustomerContact> contactForVehicle(UUID vehicleId);

    /// The names of the given customers in one query, keyed by customer id (ids not found are left out). Personal
    /// data: only for screens of the owner and the front desk, never for mechanics.
    java.util.Map<UUID, String> customerNames(java.util.Collection<UUID> customerIds);

    /// The name, tax number and address of the customer who owns the vehicle, for the snapshot of an invoice; empty
    /// when the vehicle is not found in the workshop.
    Optional<CustomerFiscalData> fiscalDataForVehicle(UUID vehicleId);

    /// Records a newer odometer reading, ignoring readings lower than the current one.
    void recordMileage(UUID vehicleId, int mileage);
}
