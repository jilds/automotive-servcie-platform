package com.asp.platform.billing.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// The provider events already handled in the current workshop (insert only).
public interface PaymentEventRepository {

    /// Takes a lock that lasts until the end of the transaction on this event, so a concurrent copy waits for this one.
    void lock(String provider, String eventId);

    boolean exists(String provider, String eventId);

    void record(String provider, String eventId, @Nullable UUID paymentId, boolean applied, @Nullable String detail,
            Instant now);
}
