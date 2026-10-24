package com.asp.platform.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Address;

/// The whole address is optional, but when it is sent, street, postal code and locality are required.
/// A Portuguese address (country PT or left out) needs a postal code like 1100-053.
@PostalCodeForCountry
record AddressRequest(
        @NotBlank String street,
        @Nullable String detail,
        @NotBlank String postalCode,
        @NotBlank String locality,
        @Nullable @Pattern(regexp = "[A-Za-z]{2}", message = "Country must be a two-letter code") String country) {

    Address toAddress() {
        return new Address(street, detail, postalCode, locality, country == null ? Address.PORTUGAL : country);
    }

    /// The country the address is in; Portugal when left out.
    String countryOrDefault() {
        return country == null || country.isBlank() ? Address.PORTUGAL : country;
    }
}
