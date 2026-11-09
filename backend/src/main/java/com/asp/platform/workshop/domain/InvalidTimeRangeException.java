package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// A time in the future, or a stop before the start. 400 `invalid-time-range` on the field.
public class InvalidTimeRangeException extends InvalidInputException {

    public static final String CODE = "invalid-time-range";

    public InvalidTimeRangeException(String field, String message) {
        super(CODE, field, message);
    }
}
