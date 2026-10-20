package com.asp.platform.customer.domain;

import com.asp.platform.shared.NotFoundException;

public class CustomerNotFoundException extends NotFoundException {

    public CustomerNotFoundException() {
        super("customer-not-found", "Customer not found");
    }
}
