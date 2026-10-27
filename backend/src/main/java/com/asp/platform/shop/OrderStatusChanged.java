package com.asp.platform.shop;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Published when the front desk marks an order ready or cancels it, or the hold time ends, and the customer left an
/// e-mail address; listeners run after the commit (the notification module tells the customer). A cancel by the
/// customer publishes nothing.
///
/// @param language   the language the customer used on the page, or null
/// @param reason     the workshop's cancel reason (CANCELLED), else null
/// @param pickupNote the workshop's note on collecting (READY), else null
/// @param holdUntil  until when the order is kept (after READY, the new hold)
public record OrderStatusChanged(UUID tenantId, int number, String name, String email, @Nullable String language,
        Change change, @Nullable String reason, @Nullable String pickupNote, Instant holdUntil) {

    public enum Change {
        READY,
        CANCELLED,
        EXPIRED
    }

    @Override
    public String toString() {
        return "OrderStatusChanged[tenantId=" + tenantId + ", number=" + number + ", change=" + change + "]";
    }
}
