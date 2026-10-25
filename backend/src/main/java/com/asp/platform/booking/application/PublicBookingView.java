package com.asp.platform.booking.application;

import java.time.ZoneId;

import com.asp.platform.booking.domain.Booking;

/// A booking as its status link shows it to the customer.
///
/// @param timeZone  the workshop's time zone, in which the slot is shown
/// @param canCancel true while the customer may still cancel online
public record PublicBookingView(Booking booking, String workshopName, ZoneId timeZone, boolean canCancel) {
}
