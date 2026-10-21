package com.asp.platform.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/// V6 backfills the registration country of existing vehicles from their workshop. The vehicle table has
/// FORCE row-level security and migrations run as its owner without a workshop bound, so this runs the real
/// migrations on a database that already holds vehicles of two workshops.
class VehicleRegistrationMigrationTests {

    private static final String OWNER = "asp_owner";
    private static final String PT_TENANT = "00000000-0000-0000-0000-0000000000a1";
    private static final String ES_TENANT = "00000000-0000-0000-0000-0000000000b1";

    @Test
    void should_backfill_the_registration_country_from_the_workshop_under_row_level_security() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
                .withInitScript("db/init-roles.sql")) {
            postgres.start();
            migrate(postgres, "5");

            try (Connection admin = admin(postgres); Statement sql = admin.createStatement()) {
                sql.execute("""
                        insert into tenant (id, name, country, timezone, created_at) values
                            ('%1$s', 'Oficina Porto', 'PT', 'Europe/Lisbon', now()),
                            ('%2$s', 'Taller Vigo', 'ES', 'Europe/Madrid', now());
                        insert into customer (id, tenant_id, name, created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000a2', '%1$s', 'Ana', now(), now()),
                            ('00000000-0000-0000-0000-0000000000b2', '%2$s', 'Iker', now(), now());
                        insert into vehicle (id, tenant_id, customer_id, plate, created_at, updated_at) values
                            ('00000000-0000-0000-0000-0000000000a3', '%1$s', '00000000-0000-0000-0000-0000000000a2',
                             '12AB34', now(), now()),
                            ('00000000-0000-0000-0000-0000000000a4', '%1$s', '00000000-0000-0000-0000-0000000000a2',
                             'ABC123', now(), now()),
                            ('00000000-0000-0000-0000-0000000000b3', '%2$s', '00000000-0000-0000-0000-0000000000b2',
                             '1234BCD', now(), now());
                        """.formatted(PT_TENANT, ES_TENANT));
            }

            migrate(postgres, "latest");

            try (Connection admin = admin(postgres); Statement sql = admin.createStatement()) {
                Map<String, String> countries = new HashMap<>();
                try (ResultSet rows = sql.executeQuery("select plate, registration_country from vehicle")) {
                    while (rows.next()) {
                        countries.put(rows.getString(1), rows.getString(2));
                    }
                }
                // The unformatted Portuguese plate does not stop the migration.
                assertThat(countries).containsExactlyInAnyOrderEntriesOf(
                        Map.of("12AB34", "PT", "ABC123", "PT", "1234BCD", "ES"));

                try (ResultSet rls = sql.executeQuery(
                        "select relforcerowsecurity from pg_class where relname = 'vehicle'")) {
                    rls.next();
                    assertThat(rls.getBoolean(1)).as("FORCE row-level security is back on").isTrue();
                }

                assertThatThrownBy(() -> sql.execute(
                        "update vehicle set registration_date = date '2012-04-15' where plate = '12AB34'"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("vehicle_registration_date_first_of_month");
                assertThatThrownBy(() -> sql.execute(
                        "update vehicle set registration_country = 'pt' where plate = '12AB34'"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("vehicle_registration_country_iso_alpha2");
                assertThatThrownBy(() -> sql.execute(
                        "update vehicle set registration_country = null where plate = '12AB34'"))
                        .isInstanceOf(SQLException.class);
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
