package com.asp.platform.workshop.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.asp.platform.workshop.application.Dashboard;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// @param month "2026-10": the calendar month, in the workshop's time zone, the delivered figures cover
record DashboardResponse(String month, Map<WorkOrderStatus, Long> openByStatus, long carsInShop,
        long deliveredThisMonth, Revenue revenueThisMonth, List<MechanicLoad> mechanicWorkload) {

    record Revenue(long netCents, long vatCents, long grossCents) {
    }

    record MechanicLoad(UUID mechanicId, String name, long openOrders) {
    }

    static DashboardResponse of(Dashboard d) {
        Dashboard.Revenue r = d.revenueThisMonth();
        return new DashboardResponse(d.month().toString(), d.openByStatus(), d.carsInShop(), d.deliveredThisMonth(),
                new Revenue(r.netCents(), r.vatCents(), r.grossCents()),
                d.mechanicWorkload().stream()
                        .map(m -> new MechanicLoad(m.mechanicId(), m.name(), m.openOrders()))
                        .toList());
    }
}
