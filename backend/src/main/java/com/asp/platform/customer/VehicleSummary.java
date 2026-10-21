package com.asp.platform.customer;

import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// What other modules may know about a vehicle. No personal data of the owner.
///
/// @param plate               stored plate: upper case, no spaces or dashes ("12AB34")
/// @param plateDisplay        the plate as printed on the vehicle ("12-AB-34"); the stored plate when its
///                            country has no known format
/// @param plateEra            numbering series such as "PT_2005", or null when unknown
/// @param registrationCountry ISO 3166-1 alpha-2 code of the country the vehicle is registered in
/// @param registrationDate    month of first registration, when known
public record VehicleSummary(UUID id, UUID customerId, String plate, @Nullable String vin, @Nullable String make,
        @Nullable String model, @Nullable Integer year, @Nullable Integer mileage, String plateDisplay,
        @Nullable String plateEra, String registrationCountry, @Nullable YearMonth registrationDate) {

    /// "Renault Clio", or an empty string when neither is known.
    public String label() {
        return ((make == null ? "" : make) + " " + (model == null ? "" : model)).trim();
    }
}
