package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// The stock ledger keeps the product's cost at the moment of the issue (V23), so a later cost change never rewrites
/// the part cost of a delivered order.
class StockIssueUnitCostTests extends ReportTestSupport {

    private List<Long> unitCosts(Shop g, String type) {
        return asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "select unit_cost_cents from stock_movement where type = ? order by changed_at, id")) {
                s.setString(1, type);
                try (ResultSet rs = s.executeQuery()) {
                    List<Long> costs = new ArrayList<>();
                    while (rs.next()) {
                        long cost = rs.getLong(1);
                        costs.add(rs.wasNull() ? null : cost);
                    }
                    return costs;
                }
            }
        });
    }

    @Test
    void should_store_the_cost_of_the_product_on_the_issue_and_keep_it_when_the_cost_changes() throws Exception {
        Shop g = garage("Oficina Custo Congelado " + UUID.randomUUID());
        String part = product(g, "CUSTO-1", "Filtro", 2000, false, 10);
        setProductCost(g, part, 800);
        String wo = workOrder(g, vehicle(g, false), null);
        addPart(g, wo, part, 2);
        deliver(g, wo);

        assertThat(unitCosts(g, "ISSUE")).containsExactly(800L);
        assertThat(unitCosts(g, "ADJUSTMENT")).as("only issues carry a cost").containsExactly((Long) null);

        setProductCost(g, part, 900);
        String second = workOrder(g, vehicle(g, false), null);
        addPart(g, second, part, 1);
        deliver(g, second);

        assertThat(unitCosts(g, "ISSUE")).containsExactly(800L, 900L);
        String day = LocalDate.now(ZoneId.of("Europe/Lisbon")).toString();
        // 2 x 800 + 1 x 900: the first order is still costed at 800 although the product now costs 900.
        report(g, "cost-vs-quote", "from=" + day + "&to=" + day).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.summary.partsCostCents").value(2500));
        report(g, "cost-vs-quote", "from=" + day + "&to=" + day + "&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void should_not_cost_stock_issued_to_a_shop_order_as_a_work_order_part() throws Exception {
        Shop g = shop("Oficina Loja Custo " + UUID.randomUUID(), true);
        String part = product(g, "LOJA-1", "Escovas", 2000, true, 10);
        setProductCost(g, part, 700);
        String token = placeAndGetToken(g, part, 1);
        assertThat(token).isNotBlank();
        String orderId = orderId(g, 1);
        call(post("/api/v1/shop-orders/" + orderId + "/collect").content("{\"paymentMethod\":\"CASH\",\"version\":0}"), g.owner())
                .andExpect(status().isOk());

        assertThat(unitCosts(g, "ISSUE")).containsExactly(700L);
        String day = LocalDate.now(ZoneId.of("Europe/Lisbon")).toString();
        report(g, "cost-vs-quote", "from=" + day + "&to=" + day).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.partsCostCents").value(0));
    }
}
