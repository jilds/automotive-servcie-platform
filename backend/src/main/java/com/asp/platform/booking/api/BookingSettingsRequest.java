package com.asp.platform.booking.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.Closure;
import com.asp.platform.booking.domain.OpeningRange;

/// `PUT /booking/settings` (owner): the whole settings with the version read. Opening ranges must open before they
/// close and not overlap on a day (400 `invalid-opening-hours`); a closure day given twice is kept once.
record BookingSettingsRequest(
        @NotNull Integer slotMinutes,
        @NotNull @Min(1) @Max(BookingRules.MAX_CARS_PER_SLOT) Integer carsPerSlot,
        @NotNull @Min(0) @Max(BookingRules.MAX_HOURS) Integer leadTimeHours,
        @NotNull @Min(1) @Max(BookingRules.MAX_HORIZON_DAYS) Integer horizonDays,
        @NotNull @Min(0) @Max(BookingRules.MAX_HOURS) Integer cancelUntilHours,
        @NotNull @Size(max = 70) List<@Valid @NotNull Hours> openingHours,
        @NotNull @Size(max = 400) List<@Valid @NotNull Day> closures,
        @NotNull @Nullable Long version) {

    record Hours(@NotNull @Min(1) @Max(7) Integer weekday, @NotNull LocalTime opens, @NotNull LocalTime closes) {
    }

    record Day(@NotNull LocalDate day, @Nullable @Size(max = Closure.MAX_REASON) String reason) {
    }

    /// Reported as `errors.slotMinutes`: 15, 30 or 60.
    @AssertTrue(message = "Slot length must be 15, 30 or 60 minutes")
    boolean isSlotMinutes() {
        return slotMinutes == null || BookingRules.SLOT_MINUTES.contains(slotMinutes);
    }

    /// @throws com.asp.platform.booking.domain.InvalidOpeningHoursException for empty or overlapping ranges
    BookingRules toRules() {
        return new BookingRules(slotMinutes, carsPerSlot, leadTimeHours, horizonDays, cancelUntilHours,
                openingHours.stream().map(h -> new OpeningRange(h.weekday(), h.opens(), h.closes())).toList(),
                closures.stream().map(d -> new Closure(d.day(), d.reason())).toList());
    }

    long requiredVersion() {
        return Versions.required(version);
    }
}
