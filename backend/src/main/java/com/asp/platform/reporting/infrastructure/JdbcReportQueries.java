package com.asp.platform.reporting.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.reporting.application.ReportQueries;
import com.asp.platform.reporting.domain.ReportPeriod;

/// Plain read-only SQL. Row-level security adds the workshop filter to every table except `tenant` and `app_user`,
/// which are filtered by the signed-in workshop's id. Money and hours are summed in the database; the report
/// service rounds them. A delivered order is `status = 'DELIVERED'` with `status_changed_at` inside the period
/// (`work_order_tenant_status_changed_idx`); DELIVERED is final, so that is the delivery instant. The statements of a
/// report run in one REPEATABLE READ transaction (the service's), so they see one snapshot.
///
/// The SQL constants are package-private so that `ReportQueryPerformanceTests` can run `EXPLAIN` on exactly what runs.
@Repository
class JdbcReportQueries implements ReportQueries {

    private static final String WORKSHOP = """
            select timezone, labour_cost_per_hour_cents, retention_months from tenant where id = :tenant""";

    /// One row per delivered order, newest first, at most `:limit` of them (the service asks for the cap plus one to
    /// know whether it was exceeded). `%1$s` is the optional mechanic filter, `%2$s` the optional approved-quote
    /// filter. Every aggregate is limited to those orders first. The standard hours are the snapshot on the line.
    static final String DELIVERED_ORDERS = """
            with delivered as (
                select o.id, o.number, o.vehicle_id, o.assigned_mechanic_id, o.status_changed_at
                from work_order o
                where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to
                %1$s
                %2$s
                order by o.status_changed_at desc, o.id
                limit :limit
            ), amounts as (
                select a.work_order_id, sum(a.net_cents) as net_cents, sum(a.vat_cents) as vat_cents
                from work_order_line_amounts a
                join delivered d on d.id = a.work_order_id
                group by a.work_order_id
            ), standard as (
                select l.work_order_id, sum(l.standard_hours) as hours
                from work_order_line l
                join delivered d on d.id = l.work_order_id
                group by l.work_order_id
            ), tracked as (
                select e.work_order_id, sum(extract(epoch from e.stopped_at - e.started_at)) as seconds
                from work_time_entry e
                join delivered d on d.id = e.work_order_id
                where e.stopped_at is not null
                group by e.work_order_id
            ), parts as (
                select m.reference_id as work_order_id,
                       sum(round(-m.quantity * coalesce(m.unit_cost_cents, 0))) as cost_cents
                from stock_movement m
                join delivered d on d.id = m.reference_id
                where m.type = 'ISSUE' and m.reference_type = 'WORK_ORDER'
                group by m.reference_id
            ), approved as (
                select distinct on (q.work_order_id) q.work_order_id, q.id, q.number, q.decided_at, q.decided_channel,
                       q.net_cents, q.total_cents
                from quote q
                join delivered d on d.id = q.work_order_id
                where q.status = 'APPROVED'
                order by q.work_order_id, q.number desc
            )
            select d.id, d.number, v.plate, d.status_changed_at, d.assigned_mechanic_id, u.name as mechanic_name,
                   ap.id as quote_id, ap.number as quote_number, ap.decided_at, ap.decided_channel,
                   ap.net_cents as quote_net_cents, ap.total_cents as quote_total_cents,
                   coalesce(am.net_cents, 0) as net_cents, coalesce(am.vat_cents, 0) as vat_cents,
                   coalesce(st.hours, 0) as standard_hours, coalesce(tr.seconds, 0) as tracked_seconds,
                   coalesce(pa.cost_cents, 0) as parts_cost_cents
            from delivered d
            join vehicle v on v.id = d.vehicle_id
            left join app_user u on u.id = d.assigned_mechanic_id and u.tenant_id = :tenant
            left join amounts am on am.work_order_id = d.id
            left join standard st on st.work_order_id = d.id
            left join tracked tr on tr.work_order_id = d.id
            left join parts pa on pa.work_order_id = d.id
            left join approved ap on ap.work_order_id = d.id
            order by d.status_changed_at desc, d.id""";

    private static final String HAS_APPROVED_QUOTE = """
            and exists (select 1 from quote aq where aq.work_order_id = o.id and aq.status = 'APPROVED')""";

    private static final String HAS_NO_APPROVED_QUOTE = """
            and not exists (select 1 from quote aq where aq.work_order_id = o.id and aq.status = 'APPROVED')""";

    /// Per mechanic: the time tracked in the period (entries that started in it), and the delivered jobs assigned
    /// to them with the standard hours (the lines' snapshot) and the time they tracked on those jobs.
    static final String MECHANIC_ACTIVITY = """
            with tracked as (
                select e.mechanic_id, sum(extract(epoch from e.stopped_at - e.started_at)) as seconds
                from work_time_entry e
                where e.stopped_at is not null and e.started_at >= :from and e.started_at < :to
                %1$s
                group by e.mechanic_id
            ), jobs as (
                select o.id, o.assigned_mechanic_id as mechanic_id, o.created_at, o.status_changed_at
                from work_order o
                where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to
                  and o.assigned_mechanic_id is not null
                %2$s
            ), job_totals as (
                select j.mechanic_id, count(*) as jobs,
                       sum(extract(epoch from j.status_changed_at - j.created_at)) as workshop_seconds
                from jobs j
                group by j.mechanic_id
            ), job_standard as (
                select j.mechanic_id, sum(l.standard_hours) as hours
                from jobs j
                join work_order_line l on l.work_order_id = j.id
                group by j.mechanic_id
            ), job_time as (
                select j.mechanic_id, sum(extract(epoch from e.stopped_at - e.started_at)) as seconds
                from jobs j
                join work_time_entry e on e.work_order_id = j.id and e.mechanic_id = j.mechanic_id
                where e.stopped_at is not null
                group by j.mechanic_id
            )
            select u.id, u.name, u.active, coalesce(t.seconds, 0) as tracked_seconds, coalesce(jt.jobs, 0) as jobs,
                   coalesce(js.hours, 0) as standard_hours, coalesce(jtm.seconds, 0) as seconds_on_jobs,
                   coalesce(jt.workshop_seconds, 0) as workshop_seconds
            from app_user u
            left join tracked t on t.mechanic_id = u.id
            left join job_totals jt on jt.mechanic_id = u.id
            left join job_standard js on js.mechanic_id = u.id
            left join job_time jtm on jtm.mechanic_id = u.id
            where u.tenant_id = :tenant
              and (t.mechanic_id is not null or jt.mechanic_id is not null %3$s)
            order by u.name, u.id""";

    private static final String TIME_BY_SERVICE = """
            select s.id, s.name, sum(extract(epoch from e.stopped_at - e.started_at)) as seconds,
                   count(distinct e.work_order_id) as orders
            from work_order o
            join work_time_entry e on e.work_order_id = o.id and e.stopped_at is not null
            join work_order_line l on l.id = e.line_id and l.work_order_id = o.id
            join service_item s on s.id = l.service_item_id
            where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to
            group by s.id, s.name""";

    /// The LABOR lines that came from a catalogue service, with the time tracked on them. Parts are not attributed to
    /// services: nothing links a part line to a service line.
    static final String SERVICE_LINES = """
            with delivered as (
                select o.id
                from work_order o
                where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to
            ), lines as (
                select a.service_item_id, count(*) as lines, count(distinct a.work_order_id) as orders,
                       sum(a.net_cents) as net_cents
                from work_order_line_amounts a
                join delivered d on d.id = a.work_order_id
                where a.service_item_id is not null and a.type = 'LABOR'
                group by a.service_item_id
            ), spent as (
                select l.service_item_id, sum(extract(epoch from e.stopped_at - e.started_at)) as seconds
                from delivered d
                join work_time_entry e on e.work_order_id = d.id and e.stopped_at is not null
                join work_order_line l on l.id = e.line_id and l.work_order_id = d.id
                where l.service_item_id is not null and l.type = 'LABOR'
                group by l.service_item_id
            )
            select s.id, s.name, ln.lines, ln.orders, ln.net_cents, coalesce(sp.seconds, 0) as seconds
            from lines ln
            join service_item s on s.id = ln.service_item_id
            left join spent sp on sp.service_item_id = ln.service_item_id""";

    /// The PART lines of the delivered orders: all of them (net), and the ones that came from a package (net, and cost
    /// from the consumed reservation of the line times the unit cost the issue kept). A line stores its package only
    /// since V23; earlier package parts count with the other parts.
    static final String PACKAGE_PARTS = """
            with delivered as (
                select o.id
                from work_order o
                where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to
            ), parts as (
                select a.line_id, a.net_cents, a.package_id is not null as from_package
                from work_order_line_amounts a
                join delivered d on d.id = a.work_order_id
                where a.type = 'PART'
            ), unit_cost as (
                select m.reference_id as work_order_id, m.product_id, max(m.unit_cost_cents) as cost
                from stock_movement m
                join delivered d on d.id = m.reference_id
                where m.type = 'ISSUE' and m.reference_type = 'WORK_ORDER'
                group by m.reference_id, m.product_id
            ), line_cost as (
                select r.source_line_id as line_id, round(sum(r.quantity) * max(uc.cost)) as cost_cents
                from stock_reservation r
                join delivered d on d.id = r.source_id
                join unit_cost uc on uc.work_order_id = r.source_id and uc.product_id = r.product_id
                where r.source = 'WORK_ORDER' and r.status = 'CONSUMED'
                group by r.source_line_id
            )
            select count(*) filter (where p.from_package) as package_lines,
                   coalesce(sum(p.net_cents) filter (where p.from_package), 0) as package_net_cents,
                   coalesce(sum(lc.cost_cents) filter (where p.from_package), 0) as package_cost_cents,
                   coalesce(sum(p.net_cents), 0) as part_net_cents
            from parts p
            left join line_cost lc on lc.line_id = p.line_id""";

    private static final String ISSUED_INVOICES = """
            select o.id, i.net_cents, i.total_cents, c.net_cents as credit_net_cents,
                   c.total_cents as credit_total_cents
            from work_order o
            join invoice i on i.work_order_id = o.id and i.kind = 'INVOICE' and i.status = 'ISSUED'
            left join invoice c on c.credit_of_id = i.id and c.kind = 'CREDIT_NOTE' and c.status = 'ISSUED'
            where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to""";

    /// A customer is the owner of the vehicle. The distinct local days (the workshop's zone) of a customer's delivered
    /// orders are put in order; `lead` gives the next LATER day, so orders of the same day never follow each other. A
    /// customer with an order on a day of the period is retained when any such day has its next day within `:months`
    /// months after it. The scan covers the period and the follow-up window after it (`:windowEnd`). Month arithmetic
    /// is done on local dates, so it does not depend on the session's zone.
    static final String RETENTION = """
            with delivered as (
                select v.customer_id, (o.status_changed_at at time zone cast(:zone as text))::date as day
                from work_order o
                join vehicle v on v.id = o.vehicle_id
                where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :windowEnd
            ), days as (
                select distinct customer_id, day from delivered
            ), following as (
                select customer_id, day, lead(day) over (partition by customer_id order by day) as next_day
                from days
            ), per_customer as (
                select customer_id,
                       bool_or(next_day is not null
                               and next_day <= (day + make_interval(months => cast(:months as int)))::date) as retained
                from following
                where day between :fromDate and :toDate
                group by customer_id
            )
            select count(*) as customers, count(*) filter (where retained) as retained from per_customer""";

    /// Days are 24 hours, so the answer does not depend on the session's zone either.
    static final String COMEBACKS = """
            select count(*)
            from work_order o
            where o.status = 'DELIVERED' and o.status_changed_at >= :from and o.status_changed_at < :to
              and exists (
                  select 1
                  from work_order p
                  where p.vehicle_id = o.vehicle_id and p.id <> o.id and p.status = 'DELIVERED'
                    and p.status_changed_at <= o.created_at
                    and o.created_at <= p.status_changed_at + make_interval(hours => cast(:hours as int))
              )""";

    private final JdbcClient jdbc;

    JdbcReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WorkshopContext workshop(UUID tenantId) {
        return jdbc.sql(WORKSHOP).param("tenant", tenantId)
                .query((rs, _) -> new WorkshopContext(ZoneId.of(rs.getString("timezone")),
                        rs.getObject("labour_cost_per_hour_cents", Long.class), rs.getInt("retention_months")))
                .single();
    }

    @Override
    public List<DeliveredOrder> deliveredOrders(UUID tenantId, ReportPeriod period, @Nullable UUID mechanicId,
            @Nullable Boolean hasApprovedQuote, int limit) {
        String mechanicFilter = mechanicId == null ? "" : "and o.assigned_mechanic_id = :mechanicId";
        String quoteFilter = hasApprovedQuote == null ? ""
                : hasApprovedQuote ? HAS_APPROVED_QUOTE : HAS_NO_APPROVED_QUOTE;
        JdbcClient.StatementSpec sql = range(jdbc.sql(DELIVERED_ORDERS.formatted(mechanicFilter, quoteFilter)), period)
                .param("tenant", tenantId).param("limit", limit);
        if (mechanicId != null) {
            sql = sql.param("mechanicId", mechanicId);
        }
        return sql.query((rs, _) -> {
            UUID quoteId = rs.getObject("quote_id", UUID.class);
            ApprovedQuote quote = quoteId == null ? null
                    : new ApprovedQuote(quoteId, rs.getInt("quote_number"),
                            rs.getObject("decided_at", OffsetDateTime.class).toInstant(),
                            rs.getString("decided_channel"), rs.getLong("quote_net_cents"),
                            rs.getLong("quote_total_cents"));
            return new DeliveredOrder(rs.getObject("id", UUID.class), rs.getInt("number"), rs.getString("plate"),
                    rs.getObject("status_changed_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("assigned_mechanic_id", UUID.class), rs.getString("mechanic_name"), quote,
                    rs.getLong("net_cents"), rs.getLong("vat_cents"), rs.getBigDecimal("standard_hours"),
                    seconds(rs, "tracked_seconds"), rs.getLong("parts_cost_cents"));
        }).list();
    }

    @Override
    public List<MechanicActivity> mechanicActivity(UUID tenantId, ReportPeriod period, @Nullable UUID mechanicId) {
        String sql = MECHANIC_ACTIVITY.formatted(
                mechanicId == null ? "" : "and e.mechanic_id = :mechanicId",
                mechanicId == null ? "" : "and o.assigned_mechanic_id = :mechanicId",
                mechanicId == null ? "" : "or u.id = :mechanicId");
        JdbcClient.StatementSpec spec = range(jdbc.sql(sql), period).param("tenant", tenantId);
        if (mechanicId != null) {
            spec = spec.param("mechanicId", mechanicId);
        }
        return spec.query((rs, _) -> new MechanicActivity(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getBoolean("active"), seconds(rs, "tracked_seconds"), rs.getLong("jobs"),
                rs.getBigDecimal("standard_hours"), seconds(rs, "seconds_on_jobs"),
                seconds(rs, "workshop_seconds"))).list();
    }

    @Override
    public List<ServiceTime> timeByService(ReportPeriod period) {
        return range(jdbc.sql(TIME_BY_SERVICE), period)
                .query((rs, _) -> new ServiceTime(rs.getObject("id", UUID.class), rs.getString("name"),
                        seconds(rs, "seconds"), rs.getLong("orders")))
                .list();
    }

    @Override
    public List<ServiceLines> serviceLines(ReportPeriod period) {
        return range(jdbc.sql(SERVICE_LINES), period)
                .query((rs, _) -> new ServiceLines(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("lines"), rs.getLong("orders"), rs.getLong("net_cents"), seconds(rs, "seconds")))
                .list();
    }

    @Override
    public PartLines partLines(ReportPeriod period) {
        return range(jdbc.sql(PACKAGE_PARTS), period)
                .query((rs, _) -> new PartLines(rs.getLong("package_lines"), rs.getLong("package_net_cents"),
                        rs.getBigDecimal("package_cost_cents").setScale(0, RoundingMode.HALF_UP).longValueExact(),
                        rs.getLong("part_net_cents")))
                .single();
    }

    @Override
    public List<InvoicedOrder> issuedInvoices(ReportPeriod period) {
        return range(jdbc.sql(ISSUED_INVOICES), period)
                .query((rs, _) -> new InvoicedOrder(rs.getObject("id", UUID.class), rs.getLong("net_cents"),
                        rs.getLong("total_cents"), nullableLong(rs, "credit_net_cents"),
                        nullableLong(rs, "credit_total_cents")))
                .list();
    }

    @Override
    public RetentionCounts retention(ReportPeriod period, int months) {
        // The follow-up window ends `months` months after the last day of the period.
        OffsetDateTime windowEnd = OffsetDateTime.ofInstant(period.to().plusMonths(months).plusDays(1)
                .atStartOfDay(period.zone()).toInstant(), ZoneOffset.UTC);
        return range(jdbc.sql(RETENTION), period).param("zone", period.zone().getId()).param("months", months)
                .param("windowEnd", windowEnd).param("fromDate", period.from()).param("toDate", period.to())
                .query((rs, _) -> new RetentionCounts(rs.getLong("customers"), rs.getLong("retained"))).single();
    }

    @Override
    public long comebacks(ReportPeriod period, int days) {
        return range(jdbc.sql(COMEBACKS), period).param("hours", days * 24)
                .query(Long.class).single();
    }

    private static JdbcClient.StatementSpec range(JdbcClient.StatementSpec sql, ReportPeriod period) {
        return sql.param("from", OffsetDateTime.ofInstant(period.start(), ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(period.endExclusive(), ZoneOffset.UTC));
    }

    /// Summed epoch seconds (numeric, with fractions of a second) as whole seconds, rounded once on the sum.
    private static long seconds(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? 0 : value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static @Nullable Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
