package com.asp.platform.customer.domain;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.InvalidNifException;
import com.asp.platform.shared.PortugueseNif;

/// How to reach a customer, and whether they agreed to marketing messages (GDPR).
public record ContactDetails(String name, @Nullable String nif, @Nullable String phone, @Nullable String email,
        @Nullable Address address, boolean marketingConsent) {

    public ContactDetails {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        name = name.trim();
        nif = nif == null || nif.isBlank() ? null : nif.trim();
    }

    /// Checks the NIF against the customer's country: the address country, or the workshop's when there is no
    /// address. Only Portuguese NIFs are checked. Called when contact details are written, so NIFs stored before
    /// the check existed stay readable until someone edits the customer.
    ///
    /// @throws InvalidNifException when a Portuguese NIF has a wrong check digit or first digit
    public void checkNif(String workshopCountry) {
        PortugueseNif.check(nif, address == null ? workshopCountry : address.country());
    }
}
