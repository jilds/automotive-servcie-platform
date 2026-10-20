package com.asp.platform.customer.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.ContactDetails;
import com.asp.platform.customer.domain.Customer;

record CustomerResponse(UUID id, String name, @Nullable String nif, @Nullable String phone, @Nullable String email,
        @Nullable AddressResponse address, boolean marketingConsent) {

    static CustomerResponse of(Customer customer) {
        ContactDetails c = customer.contact();
        return new CustomerResponse(customer.id(), c.name(), c.nif(), c.phone(), c.email(), AddressResponse.of(c.address()),
                c.marketingConsent());
    }
}
