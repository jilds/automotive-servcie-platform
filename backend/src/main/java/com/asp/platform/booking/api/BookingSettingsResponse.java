package com.asp.platform.booking.api;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.application.SettingsView;
import com.asp.platform.booking.domain.BookingRules;

/// The booking settings; times as "08:30" in the workshop's time zone `timeZone` (IANA id). Version 0 before the
/// owner saves them.
record BookingSettingsResponse(String timeZone, int slotMinutes, int carsPerSlot, int leadTimeHours, int horizonDays,
        int cancelUntilHours, List<Hours> openingHours, List<Day> closures, long version) {

    record Hours(int weekday, String opens, String closes) {
    }

    record Day(LocalDate day, @Nullable String reason) {
    }

    static BookingSettingsResponse of(SettingsView view) {
        BookingRules r = view.rules();
        return new BookingSettingsResponse(view.timeZone().getId(), r.slotMinutes(), r.carsPerSlot(),
                r.leadTimeHours(), r.horizonDays(), r.cancelUntilHours(),
                r.openingHours().stream()
                        .map(h -> new Hours(h.weekday(), h.opens().toString(), h.closes().toString())).toList(),
                r.closures().stream().map(c -> new Day(c.day(), c.reason())).toList(), view.version());
    }
}
