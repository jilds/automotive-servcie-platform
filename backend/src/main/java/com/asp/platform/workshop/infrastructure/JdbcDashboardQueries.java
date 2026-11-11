package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.workshop.application.DashboardQueries;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Plain SQL aggregates. Row-level security adds the workshop filter, so the queries never name a tenant.
@Repository
class JdbcDashboardQueries implements DashboardQueries {

    private static final String OPEN_ORDERS = """
            select status, assigned_mechanic_id, count(*) as orders
            from work_order
            where status in (:statuses)
            group by status, assigned_mechanic_id""";

    /// The line maths (as in WorkOrderLine: net and VAT rounded per line, half up) is the shared view
    /// `work_order_line_amounts`, which the reports read too.
    private static final String DELIVERED = """
            with delivered as (
                select id
                from work_order
                where status = 'DELIVERED'
                  and status_changed_at >= :from
                  and status_changed_at < :to
            )
            select (select count(*) from delivered) as orders,
                   coalesce(sum(a.net_cents), 0) as net_cents,
                   coalesce(sum(a.vat_cents), 0) as vat_cents
            from work_order_line_amounts a
            join delivered d on d.id = a.work_order_id""";

    private final JdbcClient jdbc;

    JdbcDashboardQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OpenOrderCount> openOrders() {
        List<String> statuses = WorkOrderStatus.boardColumns().stream().map(Enum::name).toList();
        return jdbc.sql(OPEN_ORDERS)
                .param("statuses", statuses)
                .query((rs, _) -> new OpenOrderCount(WorkOrderStatus.valueOf(rs.getString("status")),
                        rs.getObject("assigned_mechanic_id", UUID.class), rs.getLong("orders")))
                .list();
    }

    @Override
    public DeliveredTotals delivered(Instant from, Instant to) {
        return jdbc.sql(DELIVERED)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query((rs, _) -> new DeliveredTotals(rs.getLong("orders"), rs.getLong("net_cents"),
                        rs.getLong("vat_cents")))
                .single();
    }
}
