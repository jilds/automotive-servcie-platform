package com.asp.platform.booking.domain;

/// Where a booking stands. REQUESTED → CONFIRMED | DECLINED | CANCELLED; CONFIRMED → CHECKED_IN | CANCELLED |
/// NO_SHOW. The others are final.
public enum BookingStatus {
    REQUESTED,
    CONFIRMED,
    CHECKED_IN,
    DECLINED,
    CANCELLED,
    NO_SHOW;

    /// REQUESTED and CONFIRMED bookings hold a place in their slot.
    public boolean holdsAPlace() {
        return this == REQUESTED || this == CONFIRMED;
    }
}
