package com.asp.platform.booking.domain;

import com.asp.platform.shared.ConflictException;

/// The customer tried to cancel after the workshop's deadline. Mapped to 409 `booking-cancel-too-late`.
public class BookingCancelTooLateException extends ConflictException {

    public static final String CODE = "booking-cancel-too-late";

    public BookingCancelTooLateException() {
        super(CODE, "It is too late to cancel this booking online; please call the workshop");
    }
}
