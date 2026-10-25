package com.asp.platform.booking.domain;

import com.asp.platform.shared.NotFoundException;

/// A service that is not offered online by the workshop (unknown, inactive or not listed). Mapped to 404 `service-not-found`.
public class BookingServiceNotFoundException extends NotFoundException {

    public static final String CODE = "service-not-found";

    public BookingServiceNotFoundException() {
        super(CODE, "Service not found");
    }
}
