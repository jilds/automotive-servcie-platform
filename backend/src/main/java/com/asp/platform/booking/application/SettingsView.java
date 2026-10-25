package com.asp.platform.booking.application;

import java.time.ZoneId;

import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.BookingSettings;

/// The booking settings with the version an edit must send.
///
/// @param timeZone the workshop's time zone, in which opening hours, closures and slots are read
public record SettingsView(BookingRules rules, ZoneId timeZone, long version) {

    static SettingsView of(BookingSettings settings, ZoneId timeZone) {
        return new SettingsView(settings.rules(), timeZone, settings.version());
    }

    static SettingsView defaults(ZoneId timeZone) {
        return new SettingsView(BookingRules.defaults(), timeZone, 0);
    }
}
