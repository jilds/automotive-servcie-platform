package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// The MB WAY phone is not a Portuguese mobile number. 400.
public class InvalidPhoneException extends InvalidInputException {

    public InvalidPhoneException() {
        super("invalid-phone", "phone", "Not a Portuguese mobile number");
    }
}
