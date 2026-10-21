package com.asp.platform.customer.domain;

import java.time.YearMonth;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.CountryCodes;

/// Where and when a vehicle was first registered.
///
/// @param country ISO 3166-1 alpha-2 code, for example "PT"; decides which plate format applies
/// @param date    month of first registration (the date strip on Portuguese plates), when known
public record VehicleRegistration(String country, @Nullable YearMonth date) {

    /// The oldest plate series the platform knows about (Portugal, 1937).
    public static final YearMonth EARLIEST = YearMonth.of(1937, 1);

    public VehicleRegistration {
        country = country.trim().toUpperCase(Locale.ROOT);
        if (!CountryCodes.isIso(country)) {
            throw new IllegalArgumentException("Registration country must be an ISO 3166-1 alpha-2 code");
        }
        if (date != null && date.isBefore(EARLIEST)) {
            throw new IllegalArgumentException("Registration date cannot be before " + EARLIEST);
        }
    }

    public boolean isIn(String otherCountry) {
        return country.equals(otherCountry);
    }
}
