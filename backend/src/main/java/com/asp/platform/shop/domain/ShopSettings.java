package com.asp.platform.shop.domain;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// A workshop's shop settings: one per workshop, created with the defaults the first time they are needed. Its row
/// is also the lock that serialises new orders of the workshop, so order numbers have no gaps or doubles.
public final class ShopSettings {

    public static final int DEFAULT_HOLD_DAYS = 3;
    public static final int MIN_HOLD_DAYS = 1;
    public static final int MAX_HOLD_DAYS = 14;
    public static final int MAX_PICKUP_NOTE = 500;

    private final UUID id;
    private final long version;
    private int holdDays;
    private @Nullable String pickupNote;

    /// @param holdDays   days an order is kept for the customer after it is placed, and again after it is ready
    /// @param pickupNote what the customer reads about collecting (hours, where), or null
    public ShopSettings(UUID id, int holdDays, @Nullable String pickupNote, long version) {
        this.id = id;
        this.version = version;
        update(holdDays, pickupNote);
    }

    /// The settings before the owner saves them: [#DEFAULT_HOLD_DAYS] days, no note, version 0.
    public static ShopSettings defaults(UUID id) {
        return new ShopSettings(id, DEFAULT_HOLD_DAYS, null, 0);
    }

    /// @throws StaleDataException when someone else saved the settings since the editor read them
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    public void update(int newHoldDays, @Nullable String newPickupNote) {
        if (newHoldDays < MIN_HOLD_DAYS || newHoldDays > MAX_HOLD_DAYS) {
            throw new IllegalArgumentException("Hold days must be " + MIN_HOLD_DAYS + " to " + MAX_HOLD_DAYS);
        }
        String note = newPickupNote == null || newPickupNote.isBlank() ? null : newPickupNote.strip();
        if (note != null && note.length() > MAX_PICKUP_NOTE) {
            throw new IllegalArgumentException("Pickup note is longer than " + MAX_PICKUP_NOTE + " characters");
        }
        this.holdDays = newHoldDays;
        this.pickupNote = note;
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public int holdDays() {
        return holdDays;
    }

    public @Nullable String pickupNote() {
        return pickupNote;
    }
}
