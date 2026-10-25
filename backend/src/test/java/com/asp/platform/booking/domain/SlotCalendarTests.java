package com.asp.platform.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/// Drop-off slots from opening hours, in the workshop's time zone, with closures, lead time, horizon, capacity and
/// daylight saving changes (Europe/Lisbon: last Sunday of March and of October; America/Sao_Paulo: no daylight
/// saving since 2019).
class SlotCalendarTests {

    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    /// Friday 30 October 2026, noon in Lisbon (UTC+0 after the October change).
    private static final Instant FRIDAY_NOON = Instant.parse("2026-10-30T12:00:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Test
    void should_cut_the_default_opening_hours_into_half_hour_slots_on_weekdays() {
        SlotCalendar calendar = new SlotCalendar(BookingRules.defaults(), LISBON);

        List<SlotCalendar.Day> days = calendar.days(LocalDate.of(2026, 10, 31), 3, FRIDAY_NOON, Map.of());

        assertThat(days).extracting(SlotCalendar.Day::date)
                .containsExactly(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), MONDAY);
        assertThat(days.get(0).slots()).isEmpty(); // Saturday
        assertThat(days.get(1).slots()).isEmpty(); // Sunday
        List<SlotCalendar.Slot> monday = days.get(2).slots();
        assertThat(monday).hasSize(16);
        assertThat(monday).extracting(SlotCalendar.Slot::time).startsWith(LocalTime.of(9, 0), LocalTime.of(9, 30))
                .contains(LocalTime.of(12, 30), LocalTime.of(14, 0), LocalTime.of(17, 30))
                .doesNotContain(LocalTime.of(13, 0), LocalTime.of(18, 0));
        assertThat(monday.getFirst().start()).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
        assertThat(monday).allSatisfy(slot -> assertThat(slot.free()).isEqualTo(1));
    }

    @Test
    void should_keep_the_local_time_across_the_october_change_in_lisbon() {
        SlotCalendar calendar = new SlotCalendar(rules(30, 1, 0, 30, hours(1, 5, "09:00", "13:00")), LISBON);
        Instant now = Instant.parse("2026-10-20T00:00:00Z");

        List<SlotCalendar.Day> days = calendar.days(LocalDate.of(2026, 10, 23), 4, now, Map.of());

        // Friday in summer time (UTC+1), Monday in winter time (UTC+0): 09:00 local both days.
        assertThat(days.get(0).slots().getFirst().start()).isEqualTo(Instant.parse("2026-10-23T08:00:00Z"));
        assertThat(days.get(3).slots().getFirst().start()).isEqualTo(Instant.parse("2026-10-26T09:00:00Z"));
        assertThat(days.get(3).slots().getFirst().time()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void should_keep_the_local_time_across_the_march_change_in_lisbon() {
        SlotCalendar calendar = new SlotCalendar(rules(30, 1, 0, 30, hours(1, 5, "09:00", "13:00")), LISBON);
        Instant now = Instant.parse("2026-03-20T00:00:00Z");

        List<SlotCalendar.Day> days = calendar.days(LocalDate.of(2026, 3, 27), 4, now, Map.of());

        assertThat(days.get(0).slots().getFirst().start()).isEqualTo(Instant.parse("2026-03-27T09:00:00Z"));
        assertThat(days.get(3).slots().getFirst().start()).isEqualTo(Instant.parse("2026-03-30T08:00:00Z"));
    }

    @Test
    void should_skip_the_hour_that_does_not_exist_when_clocks_jump_forward() {
        // Sunday 29 March 2026: 01:00 becomes 02:00 in Lisbon.
        SlotCalendar calendar = new SlotCalendar(rules(60, 1, 0, 30, hours(7, 7, "00:00", "04:00")), LISBON);

        List<SlotCalendar.Slot> slots = calendar.days(LocalDate.of(2026, 3, 29), 1,
                Instant.parse("2026-03-28T00:00:00Z"), Map.of()).getFirst().slots();

        assertThat(slots).extracting(SlotCalendar.Slot::time)
                .containsExactly(LocalTime.of(0, 0), LocalTime.of(2, 0), LocalTime.of(3, 0));
        assertThat(slots).extracting(SlotCalendar.Slot::start).containsExactly(
                Instant.parse("2026-03-29T00:00:00Z"), Instant.parse("2026-03-29T01:00:00Z"),
                Instant.parse("2026-03-29T02:00:00Z"));
    }

    @Test
    void should_offer_the_repeated_hour_once_when_clocks_go_back() {
        // Sunday 25 October 2026: 02:00 becomes 01:00 in Lisbon, so 01:00 happens twice.
        SlotCalendar calendar = new SlotCalendar(rules(60, 1, 0, 30, hours(7, 7, "00:00", "04:00")), LISBON);
        Instant now = Instant.parse("2026-10-24T00:00:00Z");

        List<SlotCalendar.Slot> slots = calendar.days(LocalDate.of(2026, 10, 25), 1, now, Map.of()).getFirst()
                .slots();

        assertThat(slots).extracting(SlotCalendar.Slot::time).containsExactly(LocalTime.of(0, 0),
                LocalTime.of(1, 0), LocalTime.of(2, 0), LocalTime.of(3, 0));
        assertThat(slots).extracting(SlotCalendar.Slot::start).containsExactly(
                Instant.parse("2026-10-24T23:00:00Z"), Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-25T02:00:00Z"), Instant.parse("2026-10-25T03:00:00Z"));
        // The second 01:00 (01:00 UTC) is not a slot.
        assertThat(calendar.offers(Instant.parse("2026-10-25T01:00:00Z"), now)).isFalse();
        assertThat(calendar.offers(Instant.parse("2026-10-25T00:00:00Z"), now)).isTrue();
        // The span to count bookings in is 25 hours long that day.
        SlotCalendar.Span span = calendar.span(LocalDate.of(2026, 10, 25), 1);
        assertThat(span.from()).isEqualTo(Instant.parse("2026-10-24T23:00:00Z"));
        assertThat(span.to()).isEqualTo(Instant.parse("2026-10-26T00:00:00Z"));
    }

    @Test
    void should_use_sao_paulo_time_all_year_without_daylight_saving() {
        SlotCalendar calendar = new SlotCalendar(rules(30, 1, 0, 365, hours(1, 7, "09:00", "10:00")), SAO_PAULO);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        // Before 2019 Brazil changed clocks in early November and in February; now it stays at UTC-3.
        for (LocalDate day : List.of(LocalDate.of(2026, 2, 21), LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 30), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 9))) {
            List<SlotCalendar.Slot> slots = calendar.days(day, 1, now, Map.of()).getFirst().slots();
            assertThat(slots).as(day.toString()).extracting(SlotCalendar.Slot::start).containsExactly(
                    day.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC),
                    day.atTime(12, 30).toInstant(java.time.ZoneOffset.UTC));
        }
    }

    @Test
    void should_leave_out_slots_before_the_lead_time() {
        SlotCalendar calendar = new SlotCalendar(BookingRules.defaults(), LISBON);
        Instant mondayMorning = Instant.parse("2026-11-02T10:10:00Z");

        List<SlotCalendar.Day> days = calendar.days(MONDAY, 2, mondayMorning, Map.of());

        assertThat(days.get(0).slots()).isEmpty();
        assertThat(days.get(1).slots().getFirst().time()).isEqualTo(LocalTime.of(10, 30));
        assertThat(calendar.offers(Instant.parse("2026-11-03T10:00:00Z"), mondayMorning)).isFalse();
        assertThat(calendar.offers(Instant.parse("2026-11-03T10:30:00Z"), mondayMorning)).isTrue();
    }

    @Test
    void should_leave_out_days_after_the_horizon_and_days_in_the_past() {
        SlotCalendar calendar = new SlotCalendar(rules(60, 1, 0, 30, hours(1, 7, "09:00", "10:00")), LISBON);
        Instant now = Instant.parse("2026-11-02T00:00:00Z");

        assertThat(calendar.days(LocalDate.of(2026, 12, 2), 2, now, Map.of()))
                .extracting(day -> day.slots().size()).containsExactly(1, 0);
        assertThat(calendar.days(LocalDate.of(2026, 11, 1), 1, now, Map.of()).getFirst().slots()).isEmpty();
        assertThat(calendar.offers(Instant.parse("2026-12-03T09:00:00Z"), now)).isFalse();
    }

    @Test
    void should_leave_out_closed_days() {
        BookingRules closedMonday = new BookingRules(30, 1, 0, 30, 0, BookingRules.defaults().openingHours(),
                List.of(new Closure(MONDAY, "Feriado")));
        SlotCalendar calendar = new SlotCalendar(closedMonday, LISBON);

        assertThat(calendar.days(MONDAY, 1, FRIDAY_NOON, Map.of()).getFirst().slots()).isEmpty();
        assertThat(calendar.offers(Instant.parse("2026-11-02T09:00:00Z"), FRIDAY_NOON)).isFalse();
        assertThat(calendar.isSlot(Instant.parse("2026-11-02T09:00:00Z"), FRIDAY_NOON)).isFalse();
    }

    @Test
    void should_count_requested_and_confirmed_bookings_against_the_cars_per_slot() {
        SlotCalendar calendar = new SlotCalendar(rules(30, 2, 0, 30, hours(1, 1, "09:00", "10:00")), LISBON);
        Instant nine = Instant.parse("2026-11-02T09:00:00Z");
        Instant halfPast = Instant.parse("2026-11-02T09:30:00Z");

        List<SlotCalendar.Slot> slots = calendar.days(MONDAY, 1, FRIDAY_NOON, Map.of(nine, 2, halfPast, 1))
                .getFirst().slots();

        assertThat(slots).extracting(SlotCalendar.Slot::start).containsExactly(halfPast);
        assertThat(slots.getFirst().free()).isEqualTo(1);
    }

    @Test
    void should_offer_only_starts_on_the_grid_that_end_before_the_range_closes() {
        SlotCalendar calendar = new SlotCalendar(rules(30, 1, 0, 30, hours(1, 1, "09:00", "10:45")), LISBON);

        assertThat(calendar.days(MONDAY, 1, FRIDAY_NOON, Map.of()).getFirst().slots())
                .extracting(SlotCalendar.Slot::time)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0));
        assertThat(calendar.offers(Instant.parse("2026-11-02T09:10:00Z"), FRIDAY_NOON)).isFalse();
        assertThat(calendar.offers(Instant.parse("2026-11-02T10:30:00Z"), FRIDAY_NOON)).isFalse();
    }

    @Test
    void should_let_the_front_desk_use_any_future_slot_whatever_the_lead_time() {
        SlotCalendar calendar = new SlotCalendar(BookingRules.defaults(), LISBON);
        Instant mondayMorning = Instant.parse("2026-11-02T10:10:00Z");

        assertThat(calendar.offers(Instant.parse("2026-11-02T11:00:00Z"), mondayMorning)).isFalse();
        assertThat(calendar.isSlot(Instant.parse("2026-11-02T11:00:00Z"), mondayMorning)).isTrue();
        assertThat(calendar.isSlot(Instant.parse("2026-11-02T10:00:00Z"), mondayMorning)).isFalse();
        assertThat(calendar.isSlot(Instant.parse("2026-11-02T11:10:00Z"), mondayMorning)).isFalse();
    }

    @Test
    void should_list_for_the_front_desk_exactly_the_future_slots_it_accepts() {
        BookingRules rules = new BookingRules(30, 1, 48, 1, 0, BookingRules.defaults().openingHours(),
                List.of(new Closure(MONDAY.plusDays(1), null)));
        SlotCalendar calendar = new SlotCalendar(rules, LISBON);
        Instant mondayMorning = Instant.parse("2026-11-02T10:10:00Z");
        Instant taken = Instant.parse("2026-11-02T11:00:00Z");

        List<SlotCalendar.Day> days = calendar.staffDays(MONDAY, 10, mondayMorning, Map.of(taken, 1));

        // Today from 10:30 (not started), without the full 11:00; the closed Tuesday empty; after the horizon too.
        assertThat(days.getFirst().slots()).extracting(SlotCalendar.Slot::time)
                .startsWith(LocalTime.of(10, 30), LocalTime.of(11, 30));
        assertThat(days.get(1).slots()).isEmpty();
        assertThat(days.get(9).slots()).hasSize(16);
        assertThat(calendar.days(MONDAY, 10, mondayMorning, Map.of())).allSatisfy(d -> assertThat(d.slots()).isEmpty());
        // Every listed slot is accepted, and nothing else on the grid but full slots.
        for (SlotCalendar.Day day : days) {
            for (SlotCalendar.Slot slot : day.slots()) {
                assertThat(calendar.isSlot(slot.start(), mondayMorning)).isTrue();
            }
        }
        assertThat(calendar.isSlot(Instant.parse("2026-11-02T10:00:00Z"), mondayMorning)).isFalse();
    }

    @Test
    void should_refuse_empty_and_overlapping_opening_ranges() {
        assertThatThrownBy(() -> new OpeningRange(1, LocalTime.of(10, 0), LocalTime.of(10, 0)))
                .isInstanceOf(InvalidOpeningHoursException.class);
        assertThatThrownBy(() -> new OpeningRange(1, LocalTime.of(14, 0), LocalTime.of(9, 0)))
                .isInstanceOf(InvalidOpeningHoursException.class);
        assertThatThrownBy(() -> rules(30, 1, 0, 30, List.of(range(2, "09:00", "13:00"), range(2, "12:30", "18:00"))))
                .isInstanceOf(InvalidOpeningHoursException.class)
                .extracting("field").isEqualTo("openingHours");

        // Touching ranges, and the same hours on other days, are fine.
        BookingRules ok = rules(30, 1, 0, 30, List.of(range(2, "14:00", "18:00"), range(2, "09:00", "14:00"),
                range(3, "09:00", "14:00")));
        assertThat(ok.openingHours()).extracting(OpeningRange::opens)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(9, 0));
    }

    @Test
    void should_keep_one_closure_per_day_and_check_the_numbers() {
        BookingRules rules = new BookingRules(15, 1, 0, 30, 0, List.of(),
                List.of(new Closure(MONDAY, "Feriado"), new Closure(MONDAY, "Outro"), new Closure(MONDAY.minusDays(1),
                        " ")));

        assertThat(rules.closures()).extracting(Closure::day).containsExactly(MONDAY.minusDays(1), MONDAY);
        assertThat(rules.closures().getFirst().reason()).isNull();
        assertThat(rules.closures().get(1).reason()).isEqualTo("Feriado");
        assertThatThrownBy(() -> new BookingRules(20, 1, 0, 30, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingRules(30, 0, 0, 30, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BookingRules rules(int slotMinutes, int cars, int leadHours, int horizonDays,
            List<OpeningRange> hours) {
        return new BookingRules(slotMinutes, cars, leadHours, horizonDays, 0, hours, List.of());
    }

    /// The same range on every weekday from `first` to `last`.
    private static List<OpeningRange> hours(int first, int last, String opens, String closes) {
        List<OpeningRange> ranges = new ArrayList<>();
        for (int day = first; day <= last; day++) {
            ranges.add(range(day, opens, closes));
        }
        return ranges;
    }

    private static OpeningRange range(int weekday, String opens, String closes) {
        return new OpeningRange(weekday, LocalTime.parse(opens), LocalTime.parse(closes));
    }
}
