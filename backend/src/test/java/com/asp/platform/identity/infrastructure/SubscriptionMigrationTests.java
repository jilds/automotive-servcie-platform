package com.asp.platform.identity.infrastructure;

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

/// V7 turns self-signed-up workshops into subscribers: ACTIVE on PRO, with a unique slug made from the name.
/// Runs the real migrations on a database that already holds workshops whose names give the same slug.
class SubscriptionMigrationTests {

    private static final String OWNER = "asp_owner";

    @Test
    void should_make_existing_workshops_active_pro_with_unique_slugs_and_keep_user_guarantees() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
                .withInitScript("db/init-roles.sql")) {
            postgres.start();
            migrate(postgres, "6");

            try (Connection admin = admin(postgres); Statement sql = admin.createStatement()) {
                sql.execute("""
                        insert into tenant (id, name, country, timezone, created_at) values
                            ('00000000-0000-0000-0000-0000000000a1', 'Oficina São João, Lda.', 'PT', 'Europe/Lisbon',
                             timestamp '2026-01-01 10:00'),
                            ('00000000-0000-0000-0000-0000000000a2', 'OFICINA SAO JOAO LDA', 'PT', 'Europe/Lisbon',
                             timestamp '2026-02-01 10:00'),
                            ('00000000-0000-0000-0000-0000000000a3', '!!!', 'PT', 'Europe/Lisbon',
                             timestamp '2026-03-01 10:00');
                        insert into app_user (id, tenant_id, name, email, password_hash, role, created_at) values
                            ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', 'Ana',
                             'ana@test.pt', '{noop}x', 'OWNER', now());
                        """);
            }

            migrate(postgres, "latest");

            try (Connection admin = admin(postgres); Statement sql = admin.createStatement()) {
                Map<String, String> slugs = new HashMap<>();
                try (ResultSet rows = sql.executeQuery("select name, slug, plan, status from tenant")) {
                    while (rows.next()) {
                        slugs.put(rows.getString(1), rows.getString(2));
                        assertThat(rows.getString(3)).isEqualTo("PRO");
                        assertThat(rows.getString(4)).isEqualTo("ACTIVE");
                    }
                }
                assertThat(slugs).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "Oficina São João, Lda.", "oficina-sao-joao-lda",
                        "OFICINA SAO JOAO LDA", "oficina-sao-joao-lda-2",
                        "!!!", "workshop"));

                assertThatThrownBy(() -> sql.execute("update tenant set slug = 'workshop' where name like 'OFICINA%'"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("tenant_slug_unique");
                assertThatThrownBy(() -> sql.execute("update tenant set primary_color = 'red'"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("tenant_primary_color_hex");
                assertThatThrownBy(() -> sql.execute("update tenant set status = 'PAUSED'"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("tenant_status_known");
                // Workshop users still need a workshop and a workshop role.
                assertThatThrownBy(() -> sql.execute("update app_user set tenant_id = null"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.execute("update app_user set role = 'PLATFORM_ADMIN'"))
                        .isInstanceOf(SQLException.class);
                // One login serves both tables, so an email lives in only one of them.
                assertThatThrownBy(() -> sql.execute("""
                        insert into platform_admin (id, name, email, password_hash, created_at)
                        values ('00000000-0000-0000-0000-0000000000c1', 'Admin', 'ana@test.pt', '{noop}x', now())"""))
                        .isInstanceOf(SQLException.class).hasMessageContaining("already used");
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

    /// The container's superuser, to seed and inspect all workshops.
    private static Connection admin(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
