package com.asp.platform.booking.domain;

import com.asp.platform.shared.ConflictException;

/// A check-in from a booking that is not CONFIRMED. Mapped to 409 `booking-not-confirmed`.
public class BookingNotConfirmedException extends ConflictException {

    public static final String CODE = "booking-not-confirmed";

    public BookingNotConfirmedException() {
        super(CODE, "Only a confirmed booking can be checked in");
    }
}
