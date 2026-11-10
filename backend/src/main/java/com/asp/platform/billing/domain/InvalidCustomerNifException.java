package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// A Portuguese NIF with a wrong check digit or first digit, on the invoice customer. 400 invalid-nif.
public class InvalidCustomerNifException extends InvalidInputException {

    public InvalidCustomerNifException() {
        super("invalid-nif", "customer.nif", "Not a valid Portuguese NIF");
    }
}
