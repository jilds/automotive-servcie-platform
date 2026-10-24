package com.asp.platform.customer.api;

import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.FormattedPlate;
import com.asp.platform.customer.domain.PlateEra;
import com.asp.platform.customer.domain.PlateFormats;
import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;
import com.asp.platform.customer.domain.VehicleRegistration;

/// `plate` is the stored form ("12AB34"); `plateDisplay` is how it is printed ("12-AB-34").
/// `version` goes back with the next edit or transfer (optimistic locking).
record VehicleResponse(UUID id, UUID customerId, String plate, String plateDisplay, @Nullable PlateEra plateEra,
        String registrationCountry, @Nullable YearMonth registrationDate, @Nullable String vin,
        @Nullable String make, @Nullable String model, @Nullable Integer year, @Nullable String fuelType,
        @Nullable Integer mileage, long version) {

    static VehicleResponse of(Vehicle vehicle, PlateFormats plates) {
        VehicleDetails d = vehicle.details();
        VehicleRegistration registration = vehicle.registration();
        FormattedPlate plate = vehicle.formattedPlate(plates);
        return new VehicleResponse(vehicle.id(), vehicle.customerId(), d.plate(), plate.display(), plate.era(),
                registration.country(), registration.date(), d.vin(), d.make(), d.model(), d.year(), d.fuelType(),
                vehicle.mileage(), vehicle.version());
    }
}
