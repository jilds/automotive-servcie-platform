package com.asp.platform.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Mechanic productivity for a period (docs/api/release-4.md "Reports"). The records are the JSON of
/// `GET /api/v1/reports/productivity`.
public record ProductivityReport(LocalDate from, LocalDate to, String timezone, List<MechanicRow> mechanics,
        Totals totals) {

    /// @param trackedHours          the mechanic's closed entries that started in the period
    /// @param jobs                  delivered orders in the period assigned to the mechanic at delivery
    /// @param standardHours         the standard hours of those jobs
    /// @param trackedHoursOnJobs    the mechanic's closed entries on those jobs
    /// @param efficiency            `standardHours ÷ trackedHoursOnJobs`, null without tracked time on the jobs
    /// @param averageHoursInWorkshop average of delivered minus checked in, null without jobs
    public record MechanicRow(UUID mechanicId, String name, boolean active, BigDecimal trackedHours, long jobs,
            BigDecimal standardHours, BigDecimal trackedHoursOnJobs, @Nullable BigDecimal efficiency,
            @Nullable BigDecimal averageHoursInWorkshop) {
    }

    /// The sums of the listed rows; the efficiency and the average are recomputed from the sums.
    public record Totals(BigDecimal trackedHours, long jobs, BigDecimal standardHours, BigDecimal trackedHoursOnJobs,
            @Nullable BigDecimal efficiency, @Nullable BigDecimal averageHoursInWorkshop) {
    }
}
