package com.asp.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/// ADR 0002: every table that holds a workshop's data has a `tenant_id` column, row-level security that is
/// ENABLEd and FORCEd (so even the table owner is filtered), and the `tenant_isolation` policy.
///
/// The test reads the migrated schema, not a list of tables, so a table added by a later migration (service
/// catalogue, stock, ...) is checked as soon as it exists. A table that holds no workshop business data and is
/// filtered in code instead must be named in [#FILTERED_IN_CODE] with the reason.
class RowLevelSecurityCatalogueTests extends ApiTestSupport {

    /// Tables with a `tenant_id` that are deliberately not under row-level security.
    private static final Map<String, String> FILTERED_IN_CODE = Map.of(
            "app_user", "read at login before a workshop is known (ADR 0002)",
            "tenant_logo", "read by the public branded login page before sign-in (ADR 0003)");

    private static final Set<String> KNOWN_WORKSHOP_TABLES = Set.of("customer", "vehicle", "work_order",
            "work_order_line", "booking_settings", "opening_hours", "booking_closure", "booking", "booking_service",
            "shop_settings", "shop_order", "shop_order_line", "quote", "quote_line");

    @Autowired
    JdbcClient jdbc;

    @Test
    void should_force_row_level_security_with_the_tenant_policy_on_every_table_with_a_tenant_id() {
        List<TableSecurity> tables = jdbc.sql("""
                select c.relname                                         as name,
                       c.relrowsecurity                                  as enabled,
                       c.relforcerowsecurity                             as forced,
                       exists (select 1 from pg_policies p
                               where p.schemaname = 'public' and p.tablename = c.relname
                                 and p.policyname = 'tenant_isolation'
                                 and p.qual like '%app.tenant_id%'
                                 and p.with_check like '%app.tenant_id%') as has_policy
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public'
                  and c.relkind in ('r', 'p')
                  and exists (select 1 from pg_attribute a
                              where a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped)
                order by c.relname""")
                .query(TableSecurity.class)
                .list();

        Set<String> names = new TreeSet<>(tables.stream().map(TableSecurity::name).toList());
        assertThat(names).as("the check sees the known workshop tables").containsAll(KNOWN_WORKSHOP_TABLES);
        assertThat(names).as("exceptions still exist").containsAll(FILTERED_IN_CODE.keySet());

        assertThat(tables)
                .filteredOn(table -> !FILTERED_IN_CODE.containsKey(table.name()))
                .allSatisfy(table -> {
                    assertThat(table.enabled()).as("%s: row level security enabled", table.name()).isTrue();
                    assertThat(table.forced()).as("%s: row level security forced", table.name()).isTrue();
                    assertThat(table.hasPolicy()).as("%s: tenant_isolation policy on app.tenant_id", table.name())
                            .isTrue();
                });
    }

    record TableSecurity(String name, boolean enabled, boolean forced, boolean hasPolicy) {
    }
}
