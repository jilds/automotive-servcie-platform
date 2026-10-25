package com.asp.platform.booking.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.asp.platform.booking.domain.SlotCalendar;

/// One day of free slots; `time` is the local start time in the workshop's time zone ("09:00"), `free` the places
/// left. A day without free slots has an empty list.
record SlotDayResponse(LocalDate date, List<Slot> slots) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    record Slot(Instant start, String time, int free) {
    }

    static SlotDayResponse of(SlotCalendar.Day day) {
        return new SlotDayResponse(day.date(), day.slots().stream()
                .map(s -> new Slot(s.start(), TIME.format(s.time()), s.free()))
                .toList());
    }
}
