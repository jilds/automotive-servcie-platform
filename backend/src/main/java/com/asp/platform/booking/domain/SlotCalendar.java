package com.asp.platform.booking.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The drop-off slots of a workshop: its opening hours cut into slots of `slotMinutes`, read in its time zone, minus
/// closures, slots earlier than now plus the lead time and days after today plus the horizon. A slot starts inside a
/// range and ends at or before its end. A slot is free while fewer than `carsPerSlot` bookings in REQUESTED or
/// CONFIRMED status start at that time.
///
/// Opening hours are wall-clock times, so on a daylight saving change a slot keeps its local time and its instant
/// moves: a local time that does not exist that day (clocks jump forward) is skipped, and one that happens twice
/// (clocks go back) is offered once, at its first occurrence.
public final class SlotCalendar {

    private final BookingRules rules;
    private final ZoneId zone;

    public SlotCalendar(BookingRules rules, ZoneId zone) {
        this.rules = rules;
        this.zone = zone;
    }

    /// A slot with places left.
    ///
    /// @param time the local start time in the workshop's zone
    /// @param free places left, at least 1
    public record Slot(Instant start, LocalTime time, int free) {
    }

    /// The free slots of one day; empty when the day is closed, outside the horizon, or full.
    public record Day(LocalDate date, List<Slot> slots) {
    }

    /// The free slots a customer may book: `days` days from `from`, within the lead time and the horizon.
    ///
    /// @param taken bookings holding a place, by slot start (the count for the whole span, see [#span])
    public List<Day> days(LocalDate from, int days, Instant now, Map<Instant, Integer> taken) {
        return days(from, days, taken, date -> bookable(date, now), earliest(now), false);
    }

    /// The free slots the front desk may move a booking to: every slot that has not started yet, on a day that is
    /// not closed, whatever the lead time and horizon. Exactly the slots [#isSlot] accepts that have a place left.
    public List<Day> staffDays(LocalDate from, int days, Instant now, Map<Instant, Integer> taken) {
        return days(from, days, taken, date -> !rules.closedOn(date), now, true);
    }

    private List<Day> days(LocalDate from, int days, Map<Instant, Integer> taken,
            java.util.function.Predicate<LocalDate> open, Instant earliest, boolean strictlyAfter) {
        List<Day> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            List<Slot> slots = new ArrayList<>();
            if (open.test(date)) {
                for (Start start : startsOn(date)) {
                    int free = rules.carsPerSlot() - taken.getOrDefault(start.instant(), 0);
                    boolean inTime = strictlyAfter ? start.instant().isAfter(earliest)
                            : !start.instant().isBefore(earliest);
                    if (inTime && free > 0) {
                        slots.add(new Slot(start.instant(), start.time(), free));
                    }
                }
            }
            result.add(new Day(date, List.copyOf(slots)));
        }
        return List.copyOf(result);
    }

    /// True when `start` is a slot a customer may book now: a slot start of the opening hours, on an open day
    /// within the horizon, not earlier than now plus the lead time. Places are not checked here.
    public boolean offers(Instant start, Instant now) {
        LocalDate date = start.atZone(zone).toLocalDate();
        return bookable(date, now) && !start.isBefore(earliest(now)) && isStartOn(date, start);
    }

    /// True when `start` is a slot start of the opening hours on a day that is not closed, whatever the lead time
    /// and horizon: the front desk may move a booking to any such slot that has not started yet.
    public boolean isSlot(Instant start, Instant now) {
        LocalDate date = start.atZone(zone).toLocalDate();
        return start.isAfter(now) && !rules.closedOn(date) && isStartOn(date, start);
    }

    /// The instants from the start of `from` to the start of the day after the last day, in the workshop's zone:
    /// the span to count bookings in for [#days].
    public Span span(LocalDate from, int days) {
        return new Span(from.atStartOfDay(zone).toInstant(), from.plusDays(days).atStartOfDay(zone).toInstant());
    }

    /// From (inclusive) to (exclusive).
    public record Span(Instant from, Instant to) {
    }

    /// Today in the workshop's zone.
    public LocalDate today(Instant now) {
        return now.atZone(zone).toLocalDate();
    }

    public ZoneId zone() {
        return zone;
    }

    private Instant earliest(Instant now) {
        return now.plus(Duration.ofHours(rules.leadTimeHours()));
    }

    private boolean bookable(LocalDate date, Instant now) {
        LocalDate today = today(now);
        return !date.isBefore(today) && !date.isAfter(today.plusDays(rules.horizonDays())) && !rules.closedOn(date);
    }

    private boolean isStartOn(LocalDate date, Instant start) {
        return startsOn(date).stream().anyMatch(s -> s.instant().equals(start));
    }

    /// Every slot start of the day's opening ranges, in order, each instant once.
    private List<Start> startsOn(LocalDate date) {
        Set<Instant> seen = new LinkedHashSet<>();
        List<Start> starts = new ArrayList<>();
        int slot = rules.slotMinutes();
        for (OpeningRange range : rules.rangesOn(date.getDayOfWeek())) {
            int end = range.closes().toSecondOfDay() / 60;
            for (int minute = range.opens().toSecondOfDay() / 60; minute + slot <= end; minute += slot) {
                LocalTime time = LocalTime.of(minute / 60, minute % 60);
                LocalDateTime local = date.atTime(time);
                List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
                if (offsets.isEmpty()) {
                    continue; // In the gap of a jump forward: this local time does not exist today.
                }
                Instant instant = local.toInstant(offsets.getFirst());
                if (seen.add(instant)) {
                    starts.add(new Start(time, instant));
                }
            }
        }
        return starts;
    }

    private record Start(LocalTime time, Instant instant) {
    }
}
