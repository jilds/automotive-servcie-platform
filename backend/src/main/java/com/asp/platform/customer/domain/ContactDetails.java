package com.asp.platform.customer.domain;

import org.jspecify.annotations.Nullable;

/// How to reach a customer, and whether they agreed to marketing messages (GDPR).
public record ContactDetails(String name, @Nullable String nif, @Nullable String phone, @Nullable String email,
        @Nullable Address address, boolean marketingConsent) {

    public ContactDetails {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        name = name.trim();
    }
}
