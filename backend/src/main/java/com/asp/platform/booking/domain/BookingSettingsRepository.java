package com.asp.platform.booking.domain;

import java.util.Optional;

/// The current workshop's settings (row-level security: at most one row is visible).
public interface BookingSettingsRepository {

    /// The saved settings, or empty before the first use.
    Optional<BookingSettings> find();

    /// Creates the settings from `defaults` when the workshop has none (two requests doing it at once create one),
    /// then reads them and locks their row until the transaction ends. Waits a few seconds at most for another
    /// holder of the lock, then fails with a pessimistic locking error (409 `try-again`).
    BookingSettings lockOrCreate(BookingRules defaults);

    BookingSettings save(BookingSettings settings);
}
