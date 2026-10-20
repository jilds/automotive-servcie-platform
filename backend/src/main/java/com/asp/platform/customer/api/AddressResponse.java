package com.asp.platform.customer.api;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.domain.Address;

record AddressResponse(String street, @Nullable String detail, String postalCode, String locality, String country) {

    static @Nullable AddressResponse of(@Nullable Address address) {
        return address == null ? null : new AddressResponse(address.street(), address.detail(), address.postalCode(),
                address.locality(), address.country());
    }
}
