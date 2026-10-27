package com.asp.platform.shop.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.shop.domain.ShopSettings;
import com.asp.platform.shop.domain.ShopSettingsRepository;

/// The workshop's shop settings. Before the owner saves them they read as the defaults (3 hold days, no pickup
/// note) with version 0, and a first save with version 0 creates them.
@Service
public class ShopSettingsService {

    private final ShopSettingsRepository settings;

    ShopSettingsService(ShopSettingsRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public ShopSettings settings() {
        return settings.find().orElseGet(() -> ShopSettings.defaults(UUID.randomUUID()));
    }

    /// Takes the settings lock, so a change waits for orders being placed and the other way round.
    ///
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the settings since they were read
    @Transactional
    public ShopSettings update(int holdDays, @Nullable String pickupNote, long expectedVersion) {
        ShopSettings current = settings.lockOrCreate();
        current.requireVersion(expectedVersion);
        current.update(holdDays, pickupNote);
        return settings.save(current);
    }
}
