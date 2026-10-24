package com.asp.platform.customer.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.ContactDetails;
import com.asp.platform.shared.ValidNif;

/// `PUT /customers/{id}`: the fields of [CustomerRequest] plus the version the editor read. A different stored
/// version means someone else saved in between: 409 `stale-data`.
@ValidNif
record CustomerUpdateRequest(
        @NotBlank String name,
        @Nullable @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
        @Nullable String phone,
        @Nullable @Email String email,
        @Nullable @Valid AddressRequest address,
        @Nullable Boolean marketingConsent,
        @NotNull Long version) implements ValidNif.Subject {

    ContactDetails toContactDetails() {
        return new CustomerRequest(name, nif, phone, email, address, marketingConsent).toContactDetails();
    }

    @Override
    public @Nullable String nifCountry() {
        return address == null ? null : address.countryOrDefault();
    }
}
