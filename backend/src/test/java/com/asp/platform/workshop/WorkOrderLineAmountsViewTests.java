package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.asp.platform.reporting.ReportTestSupport;
import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.WorkOrderLine;
import com.jayway.jsonpath.JsonPath;

/// The SQL view `work_order_line_amounts` is OWNED BY THE WORKSHOP MODULE (ADR 0005): it repeats the rounding of
/// `WorkOrderLine` in SQL for the owner dashboard and for the reports, its columns are additive only, and this test, which
/// lives with the owner, keeps it equal to the domain. It gives, line by line, what `WorkOrderLine` computes (half up).
class WorkOrderLineAmountsViewTests extends ReportTestSupport {

    /// quantity, unit price (cents), discount %, VAT %: ties on purpose (x.5 cents), fractions, 0 % and 100 %.
    private static final List<String[]> LINES = List.of(
            new String[] {"1", "4000", "0", "23"},
            new String[] {"2", "1250", "10", "23"},
            new String[] {"1.5", "333", "10", "6"},
            new String[] {"0.5", "5", "0", "0"},
            new String[] {"3", "1", "50", "23"},
            new String[] {"1", "250", "0", "23"},
            new String[] {"0.01", "1", "0", "23"},
            new String[] {"2.5", "1", "0", "13"},
            new String[] {"7", "1999", "33.33", "21.5"},
            new String[] {"1", "1999", "100", "23"},
            new String[] {"12.34", "5678", "12.55", "8.75"},
            new String[] {"99999", "99999", "0", "23"},
            new String[] {"1", "0", "0", "23"});

    private record Amounts(long net, long vat, long total) {
    }

    @Test
    void should_give_the_amounts_the_domain_computes_for_every_sample_line() throws Exception {
        Shop g = garage("Oficina Vista " + UUID.randomUUID());
        String wo = workOrder(g, vehicle(g, false), null);
        for (String[] l : LINES) {
            call(post("/api/v1/work-orders/" + wo + "/lines").content("""
                    {"type":"LABOR","description":"L","quantity":%s,"unitPriceCents":%s,"discountPercent":%s,
                     "vatRate":%s}""".formatted(l[0], l[1], l[2], l[3])), g.owner()).andExpect(status().isCreated());
        }
        String body = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/work-orders/" + wo), g.owner()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        Map<String, Amounts> expected = new LinkedHashMap<>();
        List<String> ids = JsonPath.read(body, "$.lines[*].id");
        for (int i = 0; i < LINES.size(); i++) {
            String[] l = LINES.get(i);
            WorkOrderLine line = new WorkOrderLine(UUID.randomUUID(), i + 1, LineType.LABOR, "L", new BigDecimal(l[0]),
                    Long.parseLong(l[1]), new BigDecimal(l[2]), new BigDecimal(l[3]), null, null);
            expected.put(ids.get(i), new Amounts(line.netCents(), line.vatCents(), line.netCents() + line.vatCents()));
        }

        Map<String, Amounts> actual = asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    select line_id::text, net_cents, vat_cents, total_cents
                    from work_order_line_amounts where work_order_id = ?::uuid order by position""")) {
                s.setString(1, wo);
                try (ResultSet rs = s.executeQuery()) {
                    Map<String, Amounts> rows = new LinkedHashMap<>();
                    while (rs.next()) {
                        rows.put(rs.getString(1), new Amounts(rs.getLong(2), rs.getLong(3), rs.getLong(4)));
                    }
                    return rows;
                }
            }
        });

        assertThat(actual).containsExactlyEntriesOf(expected);
        // The API's own line net (the domain again) agrees too.
        List<Number> apiNets = JsonPath.read(body, "$.lines[*].netCents");
        List<Long> viewNets = new ArrayList<>();
        actual.values().forEach(a -> viewNets.add(a.net()));
        assertThat(apiNets.stream().map(Number::longValue).toList()).isEqualTo(viewNets);
    }

    @Test
    void should_only_show_the_lines_of_the_workshop_bound_to_the_connection() throws Exception {
        Shop a = garage("Oficina Vista A " + UUID.randomUUID());
        Shop b = garage("Oficina Vista B " + UUID.randomUUID());
        String woA = workOrder(a, vehicle(a, false), null);
        labour(a, woA, "Servico", 1000, 23);

        long seenByA = asWorkshop(a, c -> count(c, woA));
        long seenByB = asWorkshop(b, c -> count(c, woA));
        long seenWithoutWorkshop = jdbc.queryForObject(
                "select count(*) from work_order_line_amounts where work_order_id = ?::uuid", Long.class, woA);

        assertThat(seenByA).isEqualTo(1);
        assertThat(seenByB).as("another workshop's connection").isZero();
        assertThat(seenWithoutWorkshop).as("a connection with no workshop").isZero();
    }

    private static long count(java.sql.Connection c, String wo) throws java.sql.SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "select count(*) from work_order_line_amounts where work_order_id = ?::uuid")) {
            s.setString(1, wo);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
