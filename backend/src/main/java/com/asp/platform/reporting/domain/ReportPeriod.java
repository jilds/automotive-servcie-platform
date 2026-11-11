package com.asp.platform.reporting.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.jspecify.annotations.Nullable;

/// The days a report covers: local dates in the workshop's time zone, **both inclusive**. The instants are
/// `[from 00:00, to + 1 day 00:00)` by the zone's own rules, so a day with a clock change is 23 or 25 hours long and
/// an order delivered at 23:30 on the last day belongs to that day.
///
/// @param from first day, inclusive
/// @param to   last day, inclusive
/// @param zone the workshop's zone
public record ReportPeriod(LocalDate from, LocalDate to, ZoneId zone) {

    /// The longest period a report may cover.
    public static final int MAX_MONTHS = 24;

    /// @param from  first day, or null for the first day of the month of `to`
    /// @param to    last day, or null for `today`
    /// @param today the current day in the workshop's zone
    /// @throws InvalidReportParameterException when `from` is after `to` (reported on `from`)
    /// @throws ReportRangeTooLargeException    when the period is longer than [#MAX_MONTHS] months
    public static ReportPeriod of(@Nullable LocalDate from, @Nullable LocalDate to, ZoneId zone, LocalDate today) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new InvalidReportParameterException("from");
        }
        if (end.plusDays(1).isAfter(start.plusMonths(MAX_MONTHS))) {
            throw new ReportRangeTooLargeException();
        }
        return new ReportPeriod(start, end, zone);
    }

    public Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    /// The first instant after the period.
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
