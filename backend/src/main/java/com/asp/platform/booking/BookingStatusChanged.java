package com.asp.platform.booking;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Published when the front desk confirms, moves or declines a booking whose customer left an e-mail address;
/// listeners run after the commit (the notification module tells the customer).
///
/// @param slotStart the booked slot, after a move the new one
/// @param reason    the decline reason the customer sees, else null
public record BookingStatusChanged(UUID tenantId, int number, Instant slotStart, String name, String email,
        Change change, @Nullable String reason) {

    public enum Change {
        CONFIRMED,
        MOVED,
        DECLINED
    }

    @Override
    public String toString() {
        return "BookingStatusChanged[tenantId=" + tenantId + ", number=" + number + ", change=" + change + "]";
    }
}
