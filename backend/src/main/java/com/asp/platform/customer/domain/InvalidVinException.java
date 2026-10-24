package com.asp.platform.customer.domain;

import com.asp.platform.shared.InvalidInputException;

/// A VIN to look up does not have 17 characters. Mapped to 400 `invalid-vin` on the field `vin`.
public class InvalidVinException extends InvalidInputException {

    public static final String CODE = "invalid-vin";

    public InvalidVinException() {
        super(CODE, "vin", "A VIN has 17 characters");
    }
}
