package com.asp.platform.booking.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A booking request from the public page, already validated for shape; the privacy notice was accepted.
///
/// @param plate    as typed; normalised like the vehicles' plates before it is stored
/// @param language the page language (`pt-PT`, `en`, `pt-BR`) for the e-mail with the status link, or null
public record BookingRequestCommand(List<UUID> serviceIds, Instant slotStart, String name, @Nullable String phone,
        @Nullable String email, String plate, @Nullable String note, boolean marketingConsent,
        @Nullable String language) {
}
