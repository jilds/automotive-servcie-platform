package com.asp.platform.workshop.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Outbound port: aggregates for the owner dashboard, computed in the database. Row-level security limits
/// every query to the current workshop.
public interface DashboardQueries {

    /// Open (on-board) work orders counted per status and assigned mechanic.
    List<OpenOrderCount> openOrders();

    /// Orders delivered in `[from, to)` and the totals of their lines, with the same per-line rounding as
    /// [com.asp.platform.workshop.domain.WorkOrderLine].
    DeliveredTotals delivered(Instant from, Instant to);

    /// @param mechanicId null for unassigned orders
    record OpenOrderCount(WorkOrderStatus status, @Nullable UUID mechanicId, long orders) {
    }

    record DeliveredTotals(long orders, long netCents, long vatCents) {
    }
}
