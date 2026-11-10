package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// A query parameter outside its range. 400 validation with the parameter in errors.
public class InvalidQueryException extends InvalidInputException {

    public InvalidQueryException(String parameter) {
        super("validation", parameter, "Not a valid value");
    }
}
