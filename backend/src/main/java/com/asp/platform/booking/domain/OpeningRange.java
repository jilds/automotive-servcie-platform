package com.asp.platform.booking.domain;

import java.time.LocalTime;

/// One range of a weekday in which cars are dropped off, in the workshop's local time, for example Monday 08:30 to
/// 12:30. A day may have several (a lunch break). Seconds are dropped.
///
/// @param weekday 1 (Monday) to 7 (Sunday), as [java.time.DayOfWeek#getValue]
public record OpeningRange(int weekday, LocalTime opens, LocalTime closes) {

    /// @throws InvalidOpeningHoursException when `opens` is not before `closes`
    public OpeningRange {
        if (weekday < 1 || weekday > 7) {
            throw new IllegalArgumentException("Weekday must be 1 (Monday) to 7 (Sunday)");
        }
        opens = opens.withSecond(0).withNano(0);
        closes = closes.withSecond(0).withNano(0);
        if (!opens.isBefore(closes)) {
            throw new InvalidOpeningHoursException();
        }
    }

    boolean overlaps(OpeningRange other) {
        return weekday == other.weekday && opens.isBefore(other.closes) && other.opens.isBefore(closes);
    }
}
