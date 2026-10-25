package com.asp.platform.booking.domain;

import java.util.UUID;

import com.asp.platform.shared.StaleDataException;

/// A workshop's booking settings: one per workshop, created with [BookingRules#defaults] the first time it is
/// needed. Its row is also the lock that serialises the bookings of the workshop, so a slot never takes more cars
/// than allowed.
public final class BookingSettings {

    private final UUID id;
    private final long version;
    private BookingRules rules;

    public BookingSettings(UUID id, BookingRules rules, long version) {
        this.id = id;
        this.rules = rules;
        this.version = version;
    }

    /// @throws StaleDataException when someone else saved the settings since the editor read them
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    public void update(BookingRules newRules) {
        this.rules = newRules;
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public BookingRules rules() {
        return rules;
    }
}
