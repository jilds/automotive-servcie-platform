package com.asp.platform.booking.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.domain.Booking;

/// A booking as the front desk sees it.
///
/// @param vehicleId     the vehicle checked in from the booking, else the workshop's vehicle with the booked plate,
///                      when there is one
/// @param decidedByName who confirmed, declined, moved or marked it as a no-show last
public record BookingView(Booking booking, @Nullable UUID vehicleId, @Nullable String decidedByName) {
}
