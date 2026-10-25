package com.asp.platform.booking.application;

import java.time.Instant;

/// What the customer gets back for a request: the booking number and the status link's token, shown once.
public record BookingReceipt(int number, String statusToken, Instant slotStart) {
}
