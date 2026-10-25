package com.asp.platform.booking.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface BookingRepository {

    /// Flushes, so the returned booking carries the stored version.
    Booking save(Booking booking);

    Optional<Booking> findById(UUID id);

    /// @param tokenHash [com.asp.platform.shared.StatusTokens#hash] of the status link's token
    Optional<Booking> findByTokenHash(String tokenHash);

    /// Bookings whose slot starts in [from, to), ordered by slot start then number; any status when null.
    List<Booking> findBetween(Instant from, Instant to, @Nullable BookingStatus status);

    /// Bookings holding a place (REQUESTED, CONFIRMED) per slot start in [from, to).
    Map<Instant, Integer> placesTaken(Instant from, Instant to);

    /// Bookings holding a place at exactly this slot start.
    int placesTakenAt(Instant slotStart);

    /// Bookings waiting for the front desk (REQUESTED).
    long countRequested();

    /// The next booking number of the workshop. Call it while holding the settings lock, which serialises new
    /// bookings; the unique (tenant, number) constraint backs it up.
    int nextNumber();
}
