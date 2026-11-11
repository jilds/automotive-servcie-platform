package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.asp.platform.ApiTestSupport;
import com.asp.platform.billing.domain.DocumentKind;
import com.asp.platform.billing.domain.InvoiceStatus;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.domain.MovementType;
import com.asp.platform.stock.domain.ReservationStatus;
import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.QuoteChannel;
import com.asp.platform.workshop.domain.QuoteStatus;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// ADR 0005: `reporting` reads the other modules' tables with plain SQL (`JdbcReportQueries`). This is the guard that
/// replaces the compiler: every table and column the queries read exists, and every enum literal they compare with is
/// still a value of the owning module's enum and is allowed by the column's CHECK. When it fails, the owning module changed
/// something `reporting` reads: change `JdbcReportQueries` in the same commit (and this list).
class ReportSchemaContractTests extends ApiTestSupport {

    /// table or view -> the columns reporting reads (docs/adr/0005-reporting-reads-module-tables.md).
    private static final Map<String, List<String>> READS = Map.ofEntries(
            Map.entry("work_order", List.of("id", "number", "vehicle_id", "status", "assigned_mechanic_id",
                    "status_changed_at", "created_at")),
            Map.entry("work_order_line", List.of("id", "work_order_id", "standard_hours", "package_id",
                    "service_item_id", "type")),
            Map.entry("work_order_line_amounts", List.of("line_id", "work_order_id", "type", "service_item_id",
                    "package_id", "net_cents", "vat_cents")),
            Map.entry("work_time_entry", List.of("work_order_id", "line_id", "mechanic_id", "started_at",
                    "stopped_at")),
            Map.entry("quote", List.of("id", "work_order_id", "number", "status", "decided_at", "decided_channel",
                    "net_cents", "total_cents")),
            Map.entry("stock_movement", List.of("type", "reference_type", "reference_id", "product_id", "quantity",
                    "unit_cost_cents")),
            Map.entry("stock_reservation", List.of("source", "source_id", "source_line_id", "product_id", "quantity",
                    "status")),
            Map.entry("invoice", List.of("id", "kind", "status", "net_cents", "total_cents", "credit_of_id",
                    "work_order_id")),
            Map.entry("vehicle", List.of("id", "plate", "customer_id")),
            Map.entry("service_item", List.of("id", "name")),
            Map.entry("tenant", List.of("id", "timezone", "labour_cost_per_hour_cents", "retention_months")),
            Map.entry("app_user", List.of("id", "tenant_id", "name", "active")));

    @Autowired
    JdbcClient jdbc;

    @Test
    void should_find_every_table_and_column_that_reporting_reads() {
        List<String> missing = new ArrayList<>();
        READS.forEach((table, columns) -> {
            Set<String> found = new TreeSet<>(jdbc.sql("""
                    select column_name from information_schema.columns
                    where table_schema = 'public' and table_name = :table""").param("table", table)
                    .query(String.class).list());
            for (String column : columns) {
                if (!found.contains(column)) {
                    missing.add(table + "." + column);
                }
            }
        });

        assertThat(missing).as("reporting reads these columns (JdbcReportQueries, ADR 0005): the owning module changed "
                + "or dropped them; change the SQL in the same commit").isEmpty();
    }

    @Test
    void should_still_have_the_enum_values_that_the_queries_compare_with() {
        // Literals in JdbcReportQueries, each tied to the owning module's own enum.
        assertThat(WorkOrderStatus.valueOf("DELIVERED")).as("work_order.status DELIVERED").isNotNull();
        assertThat(QuoteStatus.valueOf("APPROVED")).as("quote.status APPROVED").isNotNull();
        assertThat(QuoteChannel.valueOf("CUSTOMER")).as("quote.decided_channel CUSTOMER").isNotNull();
        assertThat(QuoteChannel.valueOf("STAFF")).as("quote.decided_channel STAFF").isNotNull();
        assertThat(MovementType.valueOf("ISSUE")).as("stock_movement.type ISSUE").isNotNull();
        assertThat(ReservationStatus.valueOf("CONSUMED")).as("stock_reservation.status CONSUMED").isNotNull();
        assertThat(ReservationSource.valueOf("WORK_ORDER")).as("stock_reservation.source WORK_ORDER").isNotNull();
        assertThat(LineType.valueOf("LABOR")).as("work_order_line.type LABOR").isNotNull();
        assertThat(LineType.valueOf("PART")).as("work_order_line.type PART").isNotNull();
        assertThat(DocumentKind.valueOf("INVOICE")).as("invoice.kind INVOICE").isNotNull();
        assertThat(DocumentKind.valueOf("CREDIT_NOTE")).as("invoice.kind CREDIT_NOTE").isNotNull();
        assertThat(InvoiceStatus.valueOf("ISSUED")).as("invoice.status ISSUED").isNotNull();
    }

    @Test
    void should_still_allow_the_literals_in_the_check_constraints_of_the_columns() {
        List<String> missing = new ArrayList<>();
        check(missing, "quote", "APPROVED");
        check(missing, "quote", "CUSTOMER");
        check(missing, "stock_movement", "ISSUE");
        check(missing, "stock_reservation", "WORK_ORDER");
        check(missing, "stock_reservation", "CONSUMED");
        check(missing, "work_order_line", "LABOR");
        check(missing, "work_order_line", "PART");
        check(missing, "invoice", "INVOICE");
        check(missing, "invoice", "CREDIT_NOTE");
        check(missing, "invoice", "ISSUED");

        assertThat(missing).as("reporting compares with these literals (JdbcReportQueries, ADR 0005): no CHECK of the "
                + "table allows them any more").isEmpty();
    }

    /// The stock ledger's `reference_type` holds the reservation source (`StockReference(source.name(), id)`), so
    /// reporting's 'WORK_ORDER' is the stock module's own value.
    @Test
    void should_read_the_work_order_reference_type_the_stock_module_writes() {
        assertThat(ReservationSource.WORK_ORDER.name())
                .as("reporting reads stock_movement.reference_type = 'WORK_ORDER'").isEqualTo("WORK_ORDER");
    }

    @Test
    void should_have_the_snapshot_columns_that_reporting_never_recomputes() {
        for (String column : List.of("work_order_line.standard_hours", "work_order_line.package_id",
                "stock_movement.unit_cost_cents")) {
            String[] parts = column.split("\\.");
            long found = jdbc.sql("""
                    select count(*) from information_schema.columns
                    where table_schema = 'public' and table_name = :table and column_name = :column""")
                    .param("table", parts[0]).param("column", parts[1]).query(Long.class).single();
            assertThat(found).as("reporting reads " + column + " (a snapshot written by its owner)").isEqualTo(1);
        }
    }

    private void check(List<String> missing, String table, String literal) {
        long allowed = jdbc.sql("""
                select count(*) from pg_constraint
                where conrelid = cast(:table as regclass) and contype = 'c'
                  and pg_get_constraintdef(oid) like :pattern""")
                .param("table", table).param("pattern", "%'" + literal + "'%").query(Long.class).single();
        if (allowed == 0) {
            missing.add(table + " CHECK allows '" + literal + "'");
        }
    }
}
