package com.asp.platform.booking.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// How a workshop takes bookings.
///
/// @param slotMinutes      length of a drop-off slot: 15, 30 or 60
/// @param carsPerSlot      bookings one slot takes (REQUESTED and CONFIRMED count)
/// @param leadTimeHours    no slot earlier than now plus this
/// @param horizonDays      no day later than today plus this
/// @param cancelUntilHours the customer may cancel until this long before the slot
/// @param openingHours     ranges per weekday, sorted by weekday and time; on one day they must not overlap
/// @param closures         whole days without bookings, sorted, one per day (later duplicates are dropped)
public record BookingRules(int slotMinutes, int carsPerSlot, int leadTimeHours, int horizonDays,
        int cancelUntilHours, List<OpeningRange> openingHours, List<Closure> closures) {

    public static final Set<Integer> SLOT_MINUTES = Set.of(15, 30, 60);
    public static final int MAX_CARS_PER_SLOT = 50;
    public static final int MAX_HOURS = 720;
    public static final int MAX_HORIZON_DAYS = 365;

    /// @throws InvalidOpeningHoursException when two ranges of one day overlap
    public BookingRules {
        if (!SLOT_MINUTES.contains(slotMinutes)) {
            throw new IllegalArgumentException("Slot length must be 15, 30 or 60 minutes");
        }
        if (carsPerSlot < 1 || carsPerSlot > MAX_CARS_PER_SLOT || horizonDays < 1 || horizonDays > MAX_HORIZON_DAYS
                || leadTimeHours < 0 || leadTimeHours > MAX_HOURS || cancelUntilHours < 0
                || cancelUntilHours > MAX_HOURS) {
            throw new IllegalArgumentException("Booking settings out of range");
        }
        List<OpeningRange> sorted = openingHours.stream()
                .sorted(Comparator.comparingInt(OpeningRange::weekday).thenComparing(OpeningRange::opens))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).overlaps(sorted.get(i))) {
                throw new InvalidOpeningHoursException();
            }
        }
        openingHours = sorted;
        Map<LocalDate, Closure> byDay = new LinkedHashMap<>();
        closures.stream().sorted(Comparator.comparing(Closure::day)).forEach(c -> byDay.putIfAbsent(c.day(), c));
        closures = List.copyOf(byDay.values());
    }

    /// Settings before the owner saves them: 30 min, 1 car, 24 h lead time, 30 days, cancel until 24 h,
    /// Monday to Friday 09:00–13:00 and 14:00–18:00.
    public static BookingRules defaults() {
        List<OpeningRange> hours = new ArrayList<>();
        for (int day = DayOfWeek.MONDAY.getValue(); day <= DayOfWeek.FRIDAY.getValue(); day++) {
            hours.add(new OpeningRange(day, LocalTime.of(9, 0), LocalTime.of(13, 0)));
            hours.add(new OpeningRange(day, LocalTime.of(14, 0), LocalTime.of(18, 0)));
        }
        return new BookingRules(30, 1, 24, 30, 24, hours, List.of());
    }

    public boolean closedOn(LocalDate day) {
        return closures.stream().anyMatch(c -> c.day().equals(day));
    }

    public List<OpeningRange> rangesOn(DayOfWeek day) {
        return openingHours.stream().filter(r -> r.weekday() == day.getValue()).toList();
    }
}
