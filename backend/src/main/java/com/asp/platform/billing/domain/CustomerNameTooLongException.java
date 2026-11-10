package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// The customer name is longer than the 120 characters a document holds. 400 customer-name-too-long on customer.name.
public class CustomerNameTooLongException extends InvalidInputException {

    public static final int MAX_LENGTH = 120;

    public CustomerNameTooLongException() {
        super("customer-name-too-long", "customer.name", "The customer name is too long for an invoice");
    }
}
