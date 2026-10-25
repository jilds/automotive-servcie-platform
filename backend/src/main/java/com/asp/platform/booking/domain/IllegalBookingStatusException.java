package com.asp.platform.booking.domain;

import com.asp.platform.shared.ConflictException;

/// A move the booking's current status does not allow, such as confirming a declined booking. Mapped to 409 `booking-status`.
public class IllegalBookingStatusException extends ConflictException {

    public static final String CODE = "booking-status";

    public IllegalBookingStatusException() {
        super(CODE, "This booking can no longer be changed this way");
    }
}
