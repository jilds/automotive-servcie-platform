package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// A time entry names a line that is not on the work order. 400 `validation` on the field `lineId`.
public class InvalidLineException extends InvalidInputException {

    public InvalidLineException() {
        super("validation", "lineId", "The line is not on this work order");
    }
}
