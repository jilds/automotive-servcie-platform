package com.asp.platform.workshop.domain;

import com.asp.platform.shared.InvalidInputException;

/// A mechanic who was assigned is replaced or removed without saying why. Mapped to 400 `reason-required` on the
/// field `reason`.
public class ReasonRequiredException extends InvalidInputException {

    public static final String CODE = "reason-required";

    public ReasonRequiredException() {
        super(CODE, "reason", "Say why the mechanic changes");
    }
}
