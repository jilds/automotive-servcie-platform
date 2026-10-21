package com.asp.platform.workshop.application;

import java.time.YearMonth;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.VehicleSummary;

/// How a vehicle's plate is shown on work orders and the board.
///
/// @param plate               stored plate ("12AB34")
/// @param display             as printed on the vehicle ("12-AB-34")
/// @param era                 numbering series such as "PT_2005", or null when unknown
/// @param registrationCountry ISO 3166-1 alpha-2 code
/// @param registrationDate    month of first registration, when known
public record VehiclePlate(String plate, String display, @Nullable String era, String registrationCountry,
        @Nullable YearMonth registrationDate) {

    static VehiclePlate of(VehicleSummary vehicle) {
        return new VehiclePlate(vehicle.plate(), vehicle.plateDisplay(), vehicle.plateEra(),
                vehicle.registrationCountry(), vehicle.registrationDate());
    }
}
