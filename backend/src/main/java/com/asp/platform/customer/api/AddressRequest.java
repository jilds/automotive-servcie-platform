package com.asp.platform.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Address;

/// The whole address is optional, but when it is sent, street, postal code and locality are required.
record AddressRequest(
        @NotBlank String street,
        @Nullable String detail,
        @NotBlank String postalCode,
        @NotBlank String locality,
        @Nullable @Pattern(regexp = "[A-Za-z]{2}", message = "Country must be a two-letter code") String country) {

    Address toAddress() {
        return new Address(street, detail, postalCode, locality, country == null ? Address.PORTUGAL : country);
    }
}
