package com.asp.platform.booking.domain;

import com.asp.platform.shared.NotFoundException;

/// No such booking in the workshop, or an unknown status link. Mapped to 404 `booking-not-found`.
public class BookingNotFoundException extends NotFoundException {

    public static final String CODE = "booking-not-found";

    public BookingNotFoundException() {
        super(CODE, "Booking not found");
    }
}
