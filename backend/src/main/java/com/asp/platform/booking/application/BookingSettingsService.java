package com.asp.platform.booking.application;

import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.BookingSettings;
import com.asp.platform.booking.domain.BookingSettingsRepository;
import com.asp.platform.identity.WorkshopDirectory;

/// The workshop's booking settings. Before the owner saves them, they read as [BookingRules#defaults] with
/// version 0, and a first save with version 0 creates them. Every answer carries the workshop's time zone, in
/// which the opening hours are read.
@Service
public class BookingSettingsService {

    private final BookingSettingsRepository settings;
    private final WorkshopDirectory directory;

    BookingSettingsService(BookingSettingsRepository settings, WorkshopDirectory directory) {
        this.settings = settings;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public SettingsView settings(UUID tenantId) {
        ZoneId zone = directory.timezone(tenantId);
        return settings.find().map(s -> SettingsView.of(s, zone)).orElseGet(() -> SettingsView.defaults(zone));
    }

    /// Takes the settings lock, so a change waits for bookings being made and the other way round.
    ///
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the settings since they were read
    @Transactional
    public SettingsView update(UUID tenantId, BookingRules rules, long expectedVersion) {
        BookingSettings current = settings.lockOrCreate(BookingRules.defaults());
        current.requireVersion(expectedVersion);
        current.update(rules);
        return SettingsView.of(settings.save(current), directory.timezone(tenantId));
    }
}
