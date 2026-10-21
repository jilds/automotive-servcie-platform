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

    /// Line maths as in WorkOrderLine: net = round(quantity × unit price × (100 − discount) / 100), then
    /// VAT = round(net × rate / 100), rounded per line, half up (round() on positive numerics). Multiplying
    /// by 0.01 instead of dividing keeps every intermediate value exact.
    private static final String DELIVERED = """
            with delivered as (
                select id
                from work_order
                where status = 'DELIVERED'
                  and status_changed_at >= :from
                  and status_changed_at < :to
            ), line_totals as (
                select round(l.quantity * l.unit_price_cents * (100 - l.discount_percent) * 0.01) as net,
                       l.vat_rate
                from work_order_line l
                join delivered d on d.id = l.work_order_id
            )
            select (select count(*) from delivered) as orders,
                   coalesce(sum(net), 0) as net_cents,
                   coalesce(sum(round(net * vat_rate * 0.01)), 0) as vat_cents
            from line_totals""";

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
