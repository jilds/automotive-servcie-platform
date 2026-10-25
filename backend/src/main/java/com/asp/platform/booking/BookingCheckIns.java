package com.asp.platform.booking;

import java.util.List;
import java.util.UUID;

/// Public API of the booking module for the check-in at the counter: the workshop module opens a work order from a
/// confirmed booking. Both calls join the caller's transaction, so a failed check-in leaves the booking CONFIRMED.
public interface BookingCheckIns {

    /// Takes the booking for a check-in of `vehicleId` (the vehicle chosen at the counter) and returns the booked
    /// services that are still active in the catalogue, in booking order, to add as catalogue lines.
    ///
    /// @throws com.asp.platform.booking.domain.BookingNotFoundException     when the workshop has no such booking
    /// @throws com.asp.platform.booking.domain.BookingNotConfirmedException unless the booking is CONFIRMED
    List<UUID> claim(UUID bookingId, UUID vehicleId);

    /// The booking becomes CHECKED_IN with the work order it opened. Call it after [#claim], in the same
    /// transaction.
    ///
    /// @throws com.asp.platform.booking.domain.BookingNotConfirmedException unless the booking is CONFIRMED
    void markCheckedIn(UUID bookingId, UUID workOrderId);
}
