package com.asp.platform.booking.domain;

import com.asp.platform.shared.InvalidInputException;

/// The front desk asked for bookings over more than [#MAX_DAYS] days. Mapped to 400 `validation` on `to`, like a
/// field the form got wrong.
public class BookingRangeTooLongException extends InvalidInputException {

    /// Longest period the booking list covers, from `from` to `to`.
    public static final int MAX_DAYS = 124;

    public BookingRangeTooLongException() {
        super("validation", "to", "The period may cover at most " + MAX_DAYS + " days");
    }
}
