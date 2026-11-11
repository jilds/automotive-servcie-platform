package com.asp.platform.identity.domain;

import com.asp.platform.shared.InvalidInputException;

/// The request both sets the labour cost per hour and asks to clear it. 400 `validation` on
/// `labourCostPerHourCents`.
public class AmbiguousLabourCostException extends InvalidInputException {

    public AmbiguousLabourCostException() {
        super("validation", "labourCostPerHourCents", "Send either a labour cost or clearLabourCost, not both");
    }
}
