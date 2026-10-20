package com.asp.platform.customer.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.ContactDetails;

record CustomerRequest(
        @NotBlank String name,
        @Nullable @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
        @Nullable String phone,
        @Nullable @Email String email,
        @Nullable @Valid AddressRequest address,
        @Nullable Boolean marketingConsent) {

    ContactDetails toContactDetails() {
        return new ContactDetails(name, nif, phone, email, address == null ? null : address.toAddress(),
                Boolean.TRUE.equals(marketingConsent));
    }
}
