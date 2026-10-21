package com.asp.platform.workshop.application;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.asp.platform.workshop.domain.WorkOrderStatus;

/// The owner's overview of the workshop.
///
/// @param month              the calendar month, in the workshop's time zone, the delivered figures cover
/// @param openByStatus       every board column in workflow order, zeros included
/// @param carsInShop         open (on-board) work orders
/// @param deliveredThisMonth orders delivered during `month`
/// @param revenueThisMonth   line totals of those delivered orders
/// @param mechanicWorkload   active mechanics by name, with their open orders, zeros included
public record Dashboard(YearMonth month, Map<WorkOrderStatus, Long> openByStatus, long carsInShop,
        long deliveredThisMonth, Revenue revenueThisMonth, List<MechanicLoad> mechanicWorkload) {

    public record Revenue(long netCents, long vatCents) {

        public long grossCents() {
            return netCents + vatCents;
        }
    }

    public record MechanicLoad(UUID mechanicId, String name, long openOrders) {
    }
}
