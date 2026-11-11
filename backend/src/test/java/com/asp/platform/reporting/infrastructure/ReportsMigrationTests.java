package com.asp.platform.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/// V23 gives existing ISSUE movements the product's current cost (the best there is), leaves the other movements
/// without one, and keeps FORCE row-level security on the ledger. Runs the real migrations on a database that
/// already holds the stock of two workshops.
class ReportsMigrationTests {

    private static final String OWNER = "asp_owner";
    private static final String T1 = "00000000-0000-0000-0000-0000000000a1";
    private static final String T2 = "00000000-0000-0000-0000-0000000000b1";
    private static final String P1 = "00000000-0000-0000-0000-0000000000a2";
    private static final String P2 = "00000000-0000-0000-0000-0000000000b2";

    @Test
    void should_backfill_the_unit_cost_of_issue_movements_from_the_product_under_row_level_security()
            throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
                .withInitScript("db/init-roles.sql")) {
            postgres.start();
            migrate(postgres, "22");

            try (Connection admin = admin(postgres); Statement sql = admin.createStatement()) {
                sql.execute("""
                        insert into tenant (id, name, country, timezone, created_at, plan, status, slug) values
                            ('%1$s', 'Oficina A', 'PT', 'Europe/Lisbon', now(), 'PRO', 'ACTIVE', 'oficina-a'),
                            ('%2$s', 'Oficina B', 'PT', 'Europe/Lisbon', now(), 'PRO', 'ACTIVE', 'oficina-b');
                        insert into product (id, tenant_id, sku, name, unit, cost_cents, sale_price_cents, vat_rate,
                                             created_at, updated_at) values
                            ('%3$s', '%1$s', 'A-1', 'Filtro', 'un', 800, 2000, 23, now(), now()),
                            ('%4$s', '%2$s', 'B-1', 'Pastilhas', 'un', 1500, 3000, 23, now(), now());
                        insert into stock_movement (id, tenant_id, product_id, quantity, type, reference_type,
                                                    reference_id, changed_by, changed_at) values
                            ('00000000-0000-0000-0000-000000000001', '%1$s', '%3$s', 10, 'RECEIPT', null, null,
                             '%1$s', now()),
                            ('00000000-0000-0000-0000-000000000002', '%1$s', '%3$s', -2, 'ISSUE', 'WORK_ORDER',
                             '%1$s', '%1$s', now()),
                            ('00000000-0000-0000-0000-000000000003', '%2$s', '%4$s', -1, 'ISSUE', 'WORK_ORDER',
                             '%2$s', '%2$s', now());
                        insert into tenant (id, name, country, timezone, created_at, plan, status, slug) values
                            ('00000000-0000-0000-0000-0000000000c1', 'Oficina C', 'BR', 'UTC+01:00', now(), 'PRO',
                             'ACTIVE', 'oficina-c'),
                            ('00000000-0000-0000-0000-0000000000d1', 'Oficina D', 'PT', '+02:00', now(), 'PRO',
                             'ACTIVE', 'oficina-d');
                        insert into customer (id, tenant_id, name, created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000a5', '%1$s', 'Ana', now(), now());
                        insert into vehicle (id, tenant_id, customer_id, plate, registration_country, created_at,
                                             updated_at) values
                            ('00000000-0000-0000-0000-0000000000a6', '%1$s', '00000000-0000-0000-0000-0000000000a5',
                             'AA00AA', 'PT', now(), now());
                        insert into work_order (id, tenant_id, number, vehicle_id, status, status_changed_at,
                                                created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000a7', '%1$s', 1,
                             '00000000-0000-0000-0000-0000000000a6', 'DELIVERED', now(), now(), now());
                        insert into service_item (id, tenant_id, name, standard_hours, price_cents, vat_rate,
                                                  duration_minutes, created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000a8', '%1$s', 'Revisao', 1.5, 4000, 23, 60, now(),
                             now());
                        insert into work_order_line (id, tenant_id, work_order_id, position, type, description, quantity,
                                                     unit_price_cents, discount_percent, vat_rate, service_item_id,
                                                     created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000b1', '%1$s', '00000000-0000-0000-0000-0000000000a7', 1,
                             'LABOR', 'Revisao', 1, 4000, 0, 23, '00000000-0000-0000-0000-0000000000a8', now(), now()),
                            ('00000000-0000-0000-0000-0000000000b2', '%1$s', '00000000-0000-0000-0000-0000000000a7', 2,
                             'LABOR', 'Pacote: Revisao × 2', 1, 8000, 0, 23, '00000000-0000-0000-0000-0000000000a8',
                             now(), now()),
                            ('00000000-0000-0000-0000-0000000000b3', '%1$s', '00000000-0000-0000-0000-0000000000a7', 3,
                             'LABOR', 'Escrito', 1, 1000, 0, 23, null, now(), now());
                        insert into tenant (id, name, country, timezone, created_at, plan, status, slug) values
                            ('00000000-0000-0000-0000-0000000000e1', 'Oficina E', 'PT', 'CET', now(), 'PRO',
                             'ACTIVE', 'oficina-e'),
                            ('00000000-0000-0000-0000-0000000000f1', 'Oficina F', 'PT', 'UTC', now(), 'PRO',
                             'ACTIVE', 'oficina-f'),
                            ('00000000-0000-0000-0000-0000000000f2', 'Oficina G', 'BR', 'Brazil/East', now(), 'PRO',
                             'ACTIVE', 'oficina-g');
                        insert into service_item (id, tenant_id, name, standard_hours, price_cents, vat_rate,
                                                  duration_minutes, created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000a9', '%1$s', 'Servico × 2', 1.5, 4000, 23, 60, now(),
                             now()),
                            ('00000000-0000-0000-0000-0000000000aa', '%1$s', 'Servico × 99999', 99.99, 4000, 23, 60,
                             now(), now());
                        insert into work_order_line (id, tenant_id, work_order_id, position, type, description, quantity,
                                                     unit_price_cents, discount_percent, vat_rate, service_item_id,
                                                     created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000b4', '%1$s', '00000000-0000-0000-0000-0000000000a7', 4,
                             'LABOR', 'Servico × 2', 1, 4000, 0, 23, '00000000-0000-0000-0000-0000000000a9', now(),
                             now()),
                            ('00000000-0000-0000-0000-0000000000b5', '%1$s', '00000000-0000-0000-0000-0000000000a7', 5,
                             'LABOR', 'Servico × 99999', 1, 4000, 0, 23, '00000000-0000-0000-0000-0000000000aa', now(),
                             now());
                        """.formatted(T1, T2, P1, P2));
            }

            migrate(postgres, "latest");

            try (Connection admin = admin(postgres); Statement sql = admin.createStatement()) {
                Map<String, Long> costs = new HashMap<>();
                try (ResultSet rows = sql.executeQuery("select id::text, unit_cost_cents from stock_movement")) {
                    while (rows.next()) {
                        String id = rows.getString(1);
                        long cost = rows.getLong(2);
                        costs.put(id, rows.wasNull() ? null : cost);
                    }
                }
                assertThat(costs).containsEntry("00000000-0000-0000-0000-000000000001", null)
                        .containsEntry("00000000-0000-0000-0000-000000000002", 800L)
                        .containsEntry("00000000-0000-0000-0000-000000000003", 1500L);

                Map<String, String> hours = new HashMap<>();
                try (ResultSet rows = sql.executeQuery("select id::text, standard_hours::text from work_order_line")) {
                    while (rows.next()) {
                        hours.put(rows.getString(1), rows.getString(2));
                    }
                }
                // The service's current 1.5 h; a trailing "x 2" on a package line doubles it; typed lines stay null.
                assertThat(hours).containsEntry("00000000-0000-0000-0000-0000000000b1", "1.50")
                        .containsEntry("00000000-0000-0000-0000-0000000000b2", "3.00")
                        .containsEntry("00000000-0000-0000-0000-0000000000b3", null);
                Map<String, String> zones = new HashMap<>();
                try (ResultSet rows = sql.executeQuery("select slug, timezone from tenant")) {
                    while (rows.next()) {
                        zones.put(rows.getString(1), rows.getString(2));
                    }
                }
                // Offsets are reset to the country's region zone; region ids stay.
                assertThat(zones).containsEntry("oficina-a", "Europe/Lisbon")
                        .containsEntry("oficina-c", "America/Sao_Paulo")
                        .containsEntry("oficina-d", "Europe/Lisbon");
                // Known limit: a service whose own name ends in " × 2", used alone, is over-counted (3.00, not 1.50).
                // A name ending in " × 99999" does not match (at most two digits) and must not abort the migration.
                assertThat(hours).containsEntry("00000000-0000-0000-0000-0000000000b4", "3.00")
                        .containsEntry("00000000-0000-0000-0000-0000000000b5", "99.99");
                Map<String, String> versions = new HashMap<>();
                try (ResultSet rows = sql.executeQuery("select slug, version::text from tenant")) {
                    while (rows.next()) {
                        versions.put(rows.getString(1), rows.getString(2));
                    }
                }
                // CET is not a region id and is reset (and its version raised); UTC stays; region ids stay untouched.
                assertThat(zones).containsEntry("oficina-e", "Europe/Lisbon").containsEntry("oficina-f", "UTC")
                        // A legacy alias that Java knows and PostgreSQL does not is reset too.
                        .containsEntry("oficina-g", "America/Sao_Paulo");
                assertThat(versions).containsEntry("oficina-c", "1").containsEntry("oficina-d", "1")
                        .containsEntry("oficina-e", "1").containsEntry("oficina-f", "0")
                        .containsEntry("oficina-a", "0");

                try (ResultSet settings = sql.executeQuery(
                        "select labour_cost_per_hour_cents, retention_months from tenant where id = '" + T1 + "'")) {
                    settings.next();
                    assertThat(settings.getObject(1)).as("labour cost is unset").isNull();
                    assertThat(settings.getInt(2)).isEqualTo(12);
                }
                try (ResultSet rls = sql.executeQuery(
                        "select relforcerowsecurity from pg_class where relname = 'stock_movement'")) {
                    rls.next();
                    assertThat(rls.getBoolean(1)).as("FORCE row-level security is still on").isTrue();
                }
            }
        }
    }

    private static void migrate(PostgreSQLContainer postgres, String target) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), OWNER, OWNER)
                .target(target)
                .load()
                .migrate();
    }

    /// The container's superuser, which bypasses row-level security, to seed and inspect all workshops.
    private static Connection admin(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
