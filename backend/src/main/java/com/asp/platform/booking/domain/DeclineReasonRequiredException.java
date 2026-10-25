package com.asp.platform.booking.domain;

import com.asp.platform.shared.InvalidInputException;

/// Declining a booking needs a reason, which the customer sees. Mapped to 400 `decline-reason-required` on `reason`.
public class DeclineReasonRequiredException extends InvalidInputException {

    public static final String CODE = "decline-reason-required";

    public DeclineReasonRequiredException() {
        super(CODE, "reason", "Say why the booking is declined");
    }
}
