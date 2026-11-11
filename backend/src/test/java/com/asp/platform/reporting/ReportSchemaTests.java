package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// Database rules of V23 and V24: the ledger always has a unit cost on an issue, the view is read-only for the app
/// role, the time zone is a region id, and the work order line snapshot cannot be negative.
class ReportSchemaTests extends ReportTestSupport {

    @Test
    void should_refuse_an_issue_movement_without_a_unit_cost() throws Exception {
        Shop g = garage("Oficina Esquema " + UUID.randomUUID());
        String product = product(g, "ESQ-1", "Filtro", 1000, false, 5);

        assertThatThrownBy(() -> asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    insert into stock_movement (id, product_id, quantity, type, changed_by, changed_at)
                    values (gen_random_uuid(), ?::uuid, -1, 'ISSUE', gen_random_uuid(), now())""")) {
                s.setString(1, product);
                return s.executeUpdate();
            }
        })).hasMessageContaining("stock_movement_issue_has_unit_cost");
        assertThatThrownBy(() -> asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    insert into stock_movement (id, product_id, quantity, type, changed_by, changed_at, unit_cost_cents)
                    values (gen_random_uuid(), ?::uuid, -1, 'ISSUE', gen_random_uuid(), now(), -5)""")) {
                s.setString(1, product);
                return s.executeUpdate();
            }
        })).hasMessageContaining("stock_movement_unit_cost_non_negative");
    }

    @Test
    void should_let_the_app_role_only_read_the_line_amounts_view() throws Exception {
        Shop g = garage("Oficina Vista Leitura " + UUID.randomUUID());
        String wo = workOrder(g, vehicle(g, false), null);
        labour(g, wo, "Servico", 1000, 23);

        for (String privilege : new String[] {"INSERT", "UPDATE", "DELETE", "TRUNCATE"}) {
            boolean granted = asWorkshop(g, c -> {
                try (PreparedStatement s = c.prepareStatement(
                        "select has_table_privilege('asp_app', 'work_order_line_amounts', ?)")) {
                    s.setString(1, privilege);
                    try (var rs = s.executeQuery()) {
                        rs.next();
                        return rs.getBoolean(1);
                    }
                }
            });
            assertThat(granted).as(privilege).isFalse();
        }
        long visible = asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement("select count(*) from work_order_line_amounts");
                    var rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        });
        assertThat(visible).isEqualTo(1);
    }

    @Test
    void should_refuse_a_time_zone_that_is_not_a_region_id_and_a_negative_standard_time() throws Exception {
        Shop g = garage("Oficina Zona Esquema " + UUID.randomUUID());

        for (String zone : new String[] {"+01:00", "UTC+01:00", "GMT+1", "CET"}) {
            assertThatThrownBy(() -> asOwner(g, c -> {
                try (PreparedStatement s = c.prepareStatement("update tenant set timezone = ? where id = ?::uuid")) {
                    s.setString(1, zone);
                    s.setString(2, g.id());
                    return s.executeUpdate();
                }
            })).as(zone).hasMessageContaining("tenant_timezone_region");
        }
        String wo = workOrder(g, vehicle(g, false), null);
        labour(g, wo, "Servico", 1000, 23);
        assertThatThrownBy(() -> asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement("update work_order_line set standard_hours = -1")) {
                return s.executeUpdate();
            }
        })).hasMessageContaining("work_order_line_standard_hours_non_negative");
    }

    @Test
    void should_cap_the_labour_cost_per_hour_in_the_database_too() throws Exception {
        Shop g = garage("Oficina Custo Maximo " + UUID.randomUUID());

        for (long cost : new long[] {1_000_001L, -1L}) {
            assertThatThrownBy(() -> asOwner(g, c -> {
                try (PreparedStatement s = c.prepareStatement(
                        "update tenant set labour_cost_per_hour_cents = ? where id = ?::uuid")) {
                    s.setLong(1, cost);
                    s.setString(2, g.id());
                    return s.executeUpdate();
                }
            })).as(String.valueOf(cost)).hasMessageContaining("tenant_labour_cost_range");
        }
    }

    /// The zone ids the application accepts (region ids) must all be known to PostgreSQL's AT TIME ZONE, or a report of
    /// a workshop that chose one would fail with a SQL error.
    @Test
    void should_know_every_accepted_time_zone_in_postgresql() throws Exception {
        java.util.Set<String> inPostgres = asWorkshop(garage("Oficina Zonas " + UUID.randomUUID()), c -> {
            java.util.Set<String> names = new java.util.HashSet<>();
            try (PreparedStatement s = c.prepareStatement("select name from pg_timezone_names");
                    java.sql.ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
            return names;
        });

        // pg_timezone_names lists the canonical names; legacy aliases (America/Buenos_Aires, ...) are not in it but PostgreSQL
        // still resolves them, so what is not listed is probed with the real AT TIME ZONE.
        java.util.List<String> unlisted = java.time.ZoneId.getAvailableZoneIds().stream()
                .filter(com.asp.platform.identity.domain.WorkshopSettings::isZoneId)
                .filter(id -> !inPostgres.contains(id)).sorted().toList();
        java.util.List<String> unusable = asWorkshop(garage("Oficina Zonas Sonda " + UUID.randomUUID()), c -> {
            java.util.List<String> bad = new java.util.ArrayList<>();
            for (String id : unlisted) {
                try (PreparedStatement s = c.prepareStatement("select timestamptz '2026-01-01' at time zone ?")) {
                    s.setString(1, id);
                    s.executeQuery().close();
                } catch (java.sql.SQLException e) {
                    bad.add(id);
                }
            }
            return bad;
        });

        assertThat(unusable).as("accepted by the application, unusable in PostgreSQL AT TIME ZONE").isEmpty();
    }

    @Test
    void should_refuse_an_offset_as_a_workshop_time_zone_in_the_settings() throws Exception {
        Shop g = garage("Oficina Zona Api " + UUID.randomUUID());

        for (String zone : new String[] {"+01:00", "UTC+01:00", "GMT+1", "CET"}) {
            call(put("/api/v1/workshop/settings").content(withVersion("""
                    {"name":"Oficina","country":"PT","timezone":"%s","defaultVatRate":23,"labourRateCents":0}"""
                    .formatted(zone), versionAt(g.owner(), "/api/v1/workshop/settings"))), g.owner())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.timezone").exists());
        }
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina","country":"PT","timezone":"Europe/Madrid","defaultVatRate":23,"labourRateCents":0}""",
                versionAt(g.owner(), "/api/v1/workshop/settings"))), g.owner()).andExpect(status().isOk());
    }
}
