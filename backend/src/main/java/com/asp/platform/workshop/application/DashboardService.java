package com.asp.platform.workshop.application;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.workshop.application.DashboardQueries.DeliveredTotals;
import com.asp.platform.workshop.application.DashboardQueries.OpenOrderCount;
import com.asp.platform.workshop.domain.WorkOrderStatus;

@Service
public class DashboardService {

    private final DashboardQueries queries;
    private final WorkshopDirectory directory;
    private final Clock clock;

    DashboardService(DashboardQueries queries, WorkshopDirectory directory, Clock clock) {
        this.queries = queries;
        this.directory = directory;
        this.clock = clock;
    }

    /// Two aggregate queries on work orders plus the mechanic list, whatever the size of the workshop.
    @Transactional(readOnly = true)
    public Dashboard dashboard(UUID tenantId) {
        ZoneId zone = directory.timezone(tenantId);
        YearMonth month = YearMonth.from(clock.instant().atZone(zone));
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        Map<WorkOrderStatus, Long> byStatus = new LinkedHashMap<>();
        WorkOrderStatus.boardColumns().forEach(status -> byStatus.put(status, 0L));
        Map<UUID, Long> byMechanic = new HashMap<>();
        for (OpenOrderCount count : queries.openOrders()) {
            byStatus.merge(count.status(), count.orders(), Long::sum);
            if (count.mechanicId() != null) {
                byMechanic.merge(count.mechanicId(), count.orders(), Long::sum);
            }
        }
        long carsInShop = byStatus.values().stream().mapToLong(Long::longValue).sum();

        List<Dashboard.MechanicLoad> workload = directory.activeMechanics(tenantId).stream()
                .map(m -> new Dashboard.MechanicLoad(m.id(), m.name(), byMechanic.getOrDefault(m.id(), 0L)))
                .toList();

        DeliveredTotals delivered = queries.delivered(from, to);
        return new Dashboard(month, byStatus, carsInShop, delivered.orders(),
                new Dashboard.Revenue(delivered.netCents(), delivered.vatCents()), workload);
    }
}
