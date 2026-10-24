package com.asp.platform.customer.domain;

import com.asp.platform.shared.InvalidInputException;

/// A Portuguese postal code not in the form NNNN-NNN. Mapped to 400 `invalid-postal-code` on `address.postalCode`.
public class InvalidPostalCodeException extends InvalidInputException {

    public static final String CODE = "invalid-postal-code";

    public InvalidPostalCodeException() {
        super(CODE, "address.postalCode", "Portuguese postal codes look like 1100-053");
    }
}
