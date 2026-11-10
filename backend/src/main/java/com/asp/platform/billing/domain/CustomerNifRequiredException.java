package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// No NIF above the final consumer limit. 400.
public class CustomerNifRequiredException extends InvalidInputException {

    public CustomerNifRequiredException() {
        super("customer-nif-required", "customer.nif", "The customer's NIF is required above the final consumer limit");
    }
}
