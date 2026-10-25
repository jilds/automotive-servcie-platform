package com.asp.platform.booking.domain;

import com.asp.platform.shared.InvalidInputException;

/// An opening range that does not open before it closes, or two ranges of one day that overlap. Mapped to 400 `invalid-opening-hours` on `openingHours`.
public class InvalidOpeningHoursException extends InvalidInputException {

    public static final String CODE = "invalid-opening-hours";

    public InvalidOpeningHoursException() {
        super(CODE, "openingHours", "Opening hours must open before they close and not overlap on a day");
    }
}
