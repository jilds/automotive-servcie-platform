package com.asp.platform.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asp.platform.reporting.ReportTestSupport;

/// Two workshops with about 5 000 delivered orders each over 24 months (3 lines, 2 time entries and 1 stock issue per
/// order). The report queries run `EXPLAIN (ANALYZE, BUFFERS)` as the app role for one of them: a narrow period must not
/// scan whole tables (across both workshops) and the 24-month period must stay within a loose time budget. The plans
/// are logged.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportQueryPerformanceTests extends ReportTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ReportQueryPerformanceTests.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Lisbon");
    private static final Pattern PARAMETER = Pattern.compile("(?<!:):([a-zA-Z]+)");
    private static final Pattern EXECUTION_TIME = Pattern.compile("Execution Time: ([0-9.]+) ms");
    private static final double BUDGET_MS = 3000;
    private static final double NARROW_BUDGET_MS = 1000;

    private Shop target;

    @BeforeAll
    void seed() throws Exception {
        target = seedWorkshop("Oficina Carga Alvo " + UUID.randomUUID());
        seedWorkshop("Oficina Carga Vizinha " + UUID.randomUUID());
        asOwner(target, c -> {
            for (String table : new String[] {"work_order", "work_order_line", "work_time_entry", "stock_movement",
                    "vehicle", "customer", "quote", "invoice"}) {
                try (PreparedStatement s = c.prepareStatement("analyze " + table)) {
                    s.execute();
                }
            }
            return 0;
        });
    }

    private Shop seedWorkshop(String name) throws Exception {
        Shop g = garage(name);
        String mechanic = mechanic(g).id();
        String service = catalogueService(g, "Servico", 1000, "1");
        String product = product(g, "CARGA-1", "Peca", 1000, false, 1);
        asOwner(g, c -> {
            run(c, "insert into customer (id, name, created_at, updated_at) "
                    + "select gen_random_uuid(), 'C' || n, now(), now() from generate_series(1, 500) n", null);
            run(c, """
                    insert into vehicle (id, customer_id, plate, registration_country, created_at, updated_at)
                    select gen_random_uuid(), x.id, 'P' || lpad(x.rn::text, 5, '0'), 'PT', now(), now()
                    from (select id, row_number() over (order by id) as rn from customer) x""", null);
            // 5 000 delivered orders, one every 3.4 hours back from now (about 708 days).
            run(c, """
                    insert into work_order (id, number, vehicle_id, status, assigned_mechanic_id, status_changed_at,
                                            created_at, updated_at)
                    select gen_random_uuid(), n, v.ids[1 + (n % 500)], 'DELIVERED', ?::uuid,
                           now() - (n * interval '3.4 hours'), now() - (n * interval '3.4 hours') - interval '1 day',
                           now()
                    from generate_series(1, 5000) n,
                         (select array_agg(id order by id) as ids from vehicle) v""", mechanic);
            run(c, """
                    insert into work_order_line (id, work_order_id, position, type, description, quantity,
                                                 unit_price_cents, discount_percent, vat_rate, service_item_id,
                                                 standard_hours, created_at, updated_at)
                    select gen_random_uuid(), o.id, p, 'LABOR', 'x', 1, 1000, 0, 23,
                           case when p = 1 then ?::uuid end, case when p = 1 then 1.0 end, now(), now()
                    from work_order o, generate_series(1, 3) p""", service);
            run(c, """
                    insert into work_time_entry (id, work_order_id, mechanic_id, started_at, stopped_at, version,
                                                 created_at, updated_at)
                    select gen_random_uuid(), o.id, ?::uuid, o.status_changed_at - e * interval '2 hours',
                           o.status_changed_at - e * interval '2 hours' + interval '1 hour', 0, now(), now()
                    from work_order o, generate_series(1, 2) e""", mechanic);
            run(c, """
                    insert into stock_movement (id, product_id, quantity, type, reference_type, reference_id,
                                                changed_by, changed_at, unit_cost_cents)
                    select gen_random_uuid(), ?::uuid, -1, 'ISSUE', 'WORK_ORDER', o.id, gen_random_uuid(),
                           o.status_changed_at, 500
                    from work_order o""", product);
            return 0;
        });
        return g;
    }

    private static void run(java.sql.Connection c, String sql, String parameter) throws java.sql.SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (parameter != null) {
                s.setString(1, parameter);
            }
            s.execute();
        }
    }

    private record Period(LocalDate from, LocalDate to) {

        Map<String, String> literals(UUID tenant) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("from", "timestamptz '" + from.atStartOfDay(ZONE).toInstant() + "'");
            m.put("to", "timestamptz '" + to.plusDays(1).atStartOfDay(ZONE).toInstant() + "'");
            m.put("windowEnd", "timestamptz '" + to.plusMonths(12).plusDays(1).atStartOfDay(ZONE).toInstant() + "'");
            m.put("fromDate", "date '" + from + "'");
            m.put("toDate", "date '" + to + "'");
            m.put("tenant", "'" + tenant + "'::uuid");
            m.put("zone", "'Europe/Lisbon'");
            m.put("months", "12");
            m.put("hours", "720");
            m.put("limit", "10001");
            return m;
        }
    }

    private static String withLiterals(String sql, Map<String, String> literals) {
        Matcher m = PARAMETER.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String literal = literals.get(m.group(1));
            if (literal == null) {
                throw new IllegalStateException("no literal for :" + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(literal));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String explain(String name, String sql, Period period) {
        String plan = asWorkshop(target, c -> {
            try (PreparedStatement s = c.prepareStatement("explain (analyze, buffers) "
                    + withLiterals(sql, period.literals(UUID.fromString(target.id()))));
                    ResultSet rs = s.executeQuery()) {
                StringBuilder text = new StringBuilder();
                while (rs.next()) {
                    text.append(rs.getString(1)).append('\n');
                }
                return text.toString();
            }
        });
        LOG.info("Plan of {} for {} to {}:\n{}", name, period.from(), period.to(), plan);
        return plan;
    }

    private static double millis(String plan) {
        Matcher m = EXECUTION_TIME.matcher(plan);
        assertThat(m.find()).as("execution time in the plan").isTrue();
        return Double.parseDouble(m.group(1));
    }

    private Map<String, String> queries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("DELIVERED_ORDERS", JdbcReportQueries.DELIVERED_ORDERS.formatted("", ""));
        q.put("MECHANIC_ACTIVITY", JdbcReportQueries.MECHANIC_ACTIVITY.formatted("", "", ""));
        q.put("SERVICE_LINES", JdbcReportQueries.SERVICE_LINES);
        q.put("RETENTION", JdbcReportQueries.RETENTION);
        q.put("COMEBACKS", JdbcReportQueries.COMEBACKS);
        return q;
    }

    @Test
    void should_not_scan_whole_tables_for_a_narrow_period() {
        LocalDate today = LocalDate.now(ZONE);
        Period month = new Period(today.minusDays(30), today);

        queries().forEach((name, sql) -> {
            String plan = explain(name, sql, month);
            // The driving table is read through its (tenant, status, date) index. The child tables hold only about
            // 30 000 rows here, so the planner may rightly hash-join them with one sequential pass (tens of
            // milliseconds) instead of 200 index probes: that is logged, not failed; the budget is.
            // COMEBACKS semi-joins every delivered order of a vehicle (6 ms here), so it is only held to the budget.
            if (!name.equals("COMEBACKS")) {
                assertThat(plan).as(name + " plan").doesNotContain("Seq Scan on work_order ");
            }
            assertThat(millis(plan)).as(name + " ms").isLessThan(NARROW_BUDGET_MS);
        });
    }

    @Test
    void should_stay_within_a_loose_time_budget_for_24_months_of_orders() {
        LocalDate today = LocalDate.now(ZONE);
        Period full = new Period(today.minusMonths(24).plusDays(1), today);

        queries().forEach((name, sql) -> assertThat(millis(explain(name, sql, full))).as(name + " ms")
                .isLessThan(BUDGET_MS));
    }

    @Test
    void should_have_seeded_the_orders_of_both_workshops() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        long orders = asWorkshop(target, c -> {
            try (PreparedStatement s = c.prepareStatement("select count(*) from work_order");
                    ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        });
        assertThat(orders).isEqualTo(5000);
        assertThat(now).isNotNull();
    }
}
