package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// Why V23 has an index (plan 08 "Indexes"): the part cost of an order reads the order's ISSUE movements in a ledger
/// that grows with every receipt, adjustment and issue of the workshop (and, as a heap, of all workshops). On a
/// seeded ledger the lookup must use the partial index, not scan the table.
class ReportQueryPlanTests extends ReportTestSupport {

    @Test
    void should_read_the_issue_movements_of_an_order_through_the_partial_index() throws Exception {
        Shop g = garage("Oficina Plano " + UUID.randomUUID());
        String product = product(g, "PLANO-1", "Filtro", 1000, false, 1);
        String wo = workOrder(g, vehicle(g, false), null);

        // 60 000 ledger rows: mostly adjustments and receipts, 20 000 issues to 20 000 different orders.
        asOwner(g, c -> {
            exec(c, """
                    insert into stock_movement (id, product_id, quantity, type, reference_type, reference_id, reason,
                                                changed_by, changed_at, unit_cost_cents)
                    select gen_random_uuid(), ?::uuid, case when n % 3 = 0 then -1 else 1 end,
                           case when n % 3 = 0 then 'ISSUE' when n % 3 = 1 then 'RECEIPT' else 'ADJUSTMENT' end,
                           case when n % 3 = 0 then 'WORK_ORDER' end,
                           case when n % 3 = 0 then gen_random_uuid() end,
                           case when n % 3 = 2 then 'inventario' end,
                           gen_random_uuid(), now() - (n || ' minutes')::interval,
                           case when n % 3 = 0 then 800 end
                    from generate_series(1, 60000) n""", product);
            exec(c, "analyze stock_movement", null);
            return 0;
        });

        String plan = asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    explain select product_id, quantity, unit_cost_cents from stock_movement
                    where type = 'ISSUE' and reference_type = 'WORK_ORDER' and reference_id = ?::uuid""")) {
                s.setString(1, wo);
                try (ResultSet rs = s.executeQuery()) {
                    StringBuilder text = new StringBuilder();
                    while (rs.next()) {
                        text.append(rs.getString(1)).append('\n');
                    }
                    return text.toString();
                }
            }
        });

        assertThat(plan).contains("stock_movement_issue_reference_idx").doesNotContain("Seq Scan");
    }

    private static void exec(Connection c, String sql, String product) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (product != null) {
                s.setString(1, product);
            }
            s.execute();
        }
    }
}
