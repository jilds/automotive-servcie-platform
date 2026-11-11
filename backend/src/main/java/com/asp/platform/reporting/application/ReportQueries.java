package com.asp.platform.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.reporting.domain.ReportPeriod;

/// What the reports read: raw figures per delivered order, per mechanic and per service, straight from the tables
/// (and the view `work_order_line_amounts`). Row-level security adds the workshop filter to everything except
/// `tenant` and `app_user`, which are filtered by the `tenantId` passed in. The rounding and the ratios are the
/// service's, so there is one implementation of them.
public interface ReportQueries {

    /// The workshop's time zone and the two report settings.
    WorkshopContext workshop(UUID tenantId);

    /// The orders delivered in the period (optionally of one mechanic, optionally only those with / without an approved
    /// quote), with their amounts, tracked time, standard hours, part cost and approved quote, newest delivery first, at
    /// most `limit` of them.
    List<DeliveredOrder> deliveredOrders(UUID tenantId, ReportPeriod period, @Nullable UUID mechanicId,
            @Nullable Boolean hasApprovedQuote, int limit);

    /// The mechanics with tracked time or delivered jobs in the period, by name; `mechanicId` limits it to one
    /// (listed even without activity).
    List<MechanicActivity> mechanicActivity(UUID tenantId, ReportPeriod period, @Nullable UUID mechanicId);

    /// Tracked time on the lines of catalogue services, on the orders delivered in the period.
    List<ServiceTime> timeByService(ReportPeriod period);

    /// The catalogue-service lines of the orders delivered in the period, with the time tracked on them.
    List<ServiceLines> serviceLines(ReportPeriod period);

    /// The PART lines of the orders delivered in the period, and the ones that came from a package.
    PartLines partLines(ReportPeriod period);

    /// The ISSUED invoice (and its ISSUED credit note) of each order delivered in the period that has one.
    List<InvoicedOrder> issuedInvoices(ReportPeriod period);

    /// Customers with an order delivered in the period, and how many of them have another delivered order on a later
    /// local day within `months` months after one of those.
    RetentionCounts retention(ReportPeriod period, int months);

    /// Orders delivered in the period whose vehicle was delivered by another order at most `days` days before this
    /// one was checked in.
    long comebacks(ReportPeriod period, int days);

    /// @param labourCostPerHourCents null when the owner has not set it
    record WorkshopContext(ZoneId zone, @Nullable Long labourCostPerHourCents, int retentionMonths) {
    }

    /// @param standardHours quantity × standard hours of the catalogue lines
    /// @param trackedSeconds closed time entries on the order
    /// @param partsCostCents the stock issued to the order at the cost it had then
    record DeliveredOrder(UUID id, int number, String plate, Instant deliveredAt, @Nullable UUID mechanicId,
            @Nullable String mechanicName, @Nullable ApprovedQuote quote, long netCents, long vatCents,
            BigDecimal standardHours, long trackedSeconds, long partsCostCents) {

        public long totalCents() {
            return netCents + vatCents;
        }
    }

    record ApprovedQuote(UUID id, int number, Instant approvedAt, String channel, long netCents, long totalCents) {
    }

    record MechanicActivity(UUID id, String name, boolean active, long trackedSeconds, long jobs,
            BigDecimal standardHours, long trackedSecondsOnJobs, long workshopSeconds) {
    }

    record ServiceTime(UUID serviceItemId, String name, long seconds, long orders) {
    }

    record ServiceLines(UUID serviceItemId, String name, long lines, long orders, long netCents, long trackedSeconds) {
    }

    /// @param creditNetCents   null when the invoice has no issued credit note
    /// @param creditTotalCents null when the invoice has no issued credit note
    record InvoicedOrder(UUID workOrderId, long netCents, long totalCents, @Nullable Long creditNetCents,
            @Nullable Long creditTotalCents) {
    }

    /// @param packageLines     part lines that came from a package (stored since V23)
    /// @param packageNetCents   their net
    /// @param packageCostCents  what their stock cost (the consumed reservations at the unit cost kept by the issue)
    /// @param partNetCents      the net of all part lines
    record PartLines(long packageLines, long packageNetCents, long packageCostCents, long partNetCents) {
    }

    record RetentionCounts(long customers, long retained) {
    }
}
