package com.asp.platform.shop.domain;

import java.util.Optional;

/// The current workshop's shop settings (row-level security: at most one row is visible).
public interface ShopSettingsRepository {

    /// The saved settings, or empty before the first use.
    Optional<ShopSettings> find();

    /// Creates the default settings when the workshop has none (two requests doing it at once create one), then
    /// reads them and locks their row until the transaction ends. Waits a few seconds at most for another holder of
    /// the lock, then fails with a pessimistic locking error (409 `try-again`).
    ShopSettings lockOrCreate();

    /// Flushes, so the returned settings carry the stored version.
    ShopSettings save(ShopSettings settings);
}
