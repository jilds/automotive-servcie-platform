package com.asp.platform.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

/// The KPIs of a period (docs/api/release-4.md "Reports"). The records are the JSON of `GET /api/v1/reports/kpis`.
/// A KPI without data yet is an [Unavailable] with the feature that will provide it.
public record KpiReport(LocalDate from, LocalDate to, String timezone, boolean labourCostSet, int retentionMonths,
        long deliveredOrders, AverageTimePerJob averageTimePerJob, AverageOrderValue averageOrderValue, Margin margin,
        Retention retention, Comebacks comebacks, Unavailable occupancy, Unavailable satisfaction,
        Unavailable complaints) {

    public static final String SOURCE = "ISSUED_INVOICES_MINUS_CREDIT_NOTES";

    /// `{"available": false, "availableWith": …}` and nothing else.
    public record Unavailable(boolean available, String availableWith) {

        public static Unavailable with(String feature) {
            return new Unavailable(false, feature);
        }
    }

    public record AverageTimePerJob(boolean available, BigDecimal trackedHours, long ordersWithTime,
            @Nullable BigDecimal hoursPerJob, List<ServiceTime> byService) {
    }

    public record ServiceTime(UUID serviceItemId, String name, BigDecimal trackedHours, long orders,
            @Nullable BigDecimal hoursPerJob) {
    }

    /// @param source          always [#SOURCE]
    /// @param invoicedOrders  delivered orders with an issued invoice that is not fully credited
    /// @param creditedOrders  delivered orders whose issued invoice is fully credited
    public record AverageOrderValue(boolean available, String source, long invoicedOrders, long creditedOrders,
            long netCents, long totalCents, @Nullable Long averageNetCents, @Nullable Long averageTotalCents,
            NotYetInvoiced notYetInvoiced) {
    }

    public record NotYetInvoiced(long orders, long netCents, long totalCents) {
    }

    /// @param basis        `PARTS_AND_LABOUR` or `PARTS_ONLY`
    /// @param packageParts the part lines that came from a package (kept on the line since V23): their net, their stock
    ///                     cost and the margin of those parts alone; part of the order margin, shown apart
    /// @param otherParts   all other parts (from stock, typed by hand, and package parts of older orders)
    public record Margin(boolean available, String basis, boolean labourCostSet, long orders, long netCents,
            long partsCostCents, @Nullable Long labourCostCents, long marginCents, @Nullable BigDecimal marginPercent,
            PackageParts packageParts, OtherParts otherParts, List<ServiceMargin> byService) {
    }

    public record PackageParts(long lines, long netCents, long partsCostCents, long marginCents,
            @Nullable BigDecimal marginPercent) {
    }

    public record OtherParts(long netCents, long partsCostCents, long marginCents, @Nullable BigDecimal marginPercent) {
    }

    public record ServiceMargin(UUID serviceItemId, String name, long lines, long orders, long netCents,
            BigDecimal trackedHours, @Nullable Long labourCostCents, long marginCents,
            @Nullable BigDecimal marginPercent) {
    }

    /// @param complete     JSON `final`: false while the follow-up window after the period end is not over yet (the rate is
    ///                     still computed from the data available, so it reads low)
    /// @param monthsCovered the months of the window that have passed since the period end (`months` when final)
    public record Retention(boolean available, int months, long customers, long retained,
            @Nullable BigDecimal ratePercent, @JsonProperty("final") boolean complete, int monthsCovered) {
    }

    public record Comebacks(boolean available, int days, long orders, @Nullable BigDecimal ratePercent) {
    }
}
