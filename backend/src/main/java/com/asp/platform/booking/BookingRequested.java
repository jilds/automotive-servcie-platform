package com.asp.platform.booking;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Published when a customer requests a booking on the public page and left an e-mail address; listeners run after
/// the commit (the notification module mails the status link). The status token exists only here and in the
/// customer's response: the database keeps its hash. Never log it.
///
/// @param language the language the customer used on the page (`pt-PT`, `en`, `pt-BR`), or null
public record BookingRequested(UUID tenantId, int number, Instant slotStart, String name, String email,
        @Nullable String language, String statusToken) {

    @Override
    public String toString() {
        return "BookingRequested[tenantId=" + tenantId + ", number=" + number + "]";
    }
}
