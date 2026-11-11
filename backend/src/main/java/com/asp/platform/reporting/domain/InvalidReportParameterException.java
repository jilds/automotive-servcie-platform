package com.asp.platform.reporting.domain;

import com.asp.platform.shared.InvalidInputException;

/// A report parameter that is not valid (a bad date, `from` after `to`, a page out of range). 400 `validation` with
/// the parameter in `errors`.
public class InvalidReportParameterException extends InvalidInputException {

    public InvalidReportParameterException(String parameter) {
        super("validation", parameter, "Not a valid value");
    }
}
