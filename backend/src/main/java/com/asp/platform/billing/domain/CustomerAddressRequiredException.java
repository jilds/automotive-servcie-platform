package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// No address above the final consumer limit. 400.
public class CustomerAddressRequiredException extends InvalidInputException {

    public CustomerAddressRequiredException() {
        super("customer-address-required", "customer.address", "The customer's address is required above the final consumer limit");
    }
}
