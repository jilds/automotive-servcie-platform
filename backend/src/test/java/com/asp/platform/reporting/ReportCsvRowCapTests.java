package com.asp.platform.reporting;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/// The CSV of the cost-vs-quote list is capped (`app.reports.csv-max-rows`, 10 000 by default; 2 here).
@TestPropertySource(properties = "app.reports.csv-max-rows=2")
class ReportCsvRowCapTests extends ReportTestSupport {

    @Test
    void should_refuse_a_csv_with_more_rows_than_the_cap_and_still_answer_json() throws Exception {
        Shop g = garage("Oficina Limite Csv");
        for (int i = 0; i < 3; i++) {
            String wo = workOrder(g, vehicle(g, false), null);
            labour(g, wo, "Servico", 1000, 23);
            deliver(g, wo);
            retime(g, wo, "2026-11-0" + (i + 1) + "T08:00:00Z", "2026-11-0" + (i + 1) + "T10:00:00Z");
        }

        report(g, "cost-vs-quote", PERIOD + "&format=csv").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("report-too-many-rows"));
        // The JSON list is capped too (the query reads at most cap + 1 rows), and so is a filtered one.
        report(g, "cost-vs-quote", PERIOD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("report-too-many-rows"))
                .andExpect(jsonPath("$.errors.format").exists());
        report(g, "cost-vs-quote", PERIOD + "&hasApprovedQuote=false").andExpect(status().isBadRequest());
        report(g, "cost-vs-quote", PERIOD + "&hasApprovedQuote=true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        report(g, "cost-vs-quote", "from=2026-11-01&to=2026-11-02&format=csv").andExpect(status().isOk());
        report(g, "cost-vs-quote", "from=2026-11-01&to=2026-11-02").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        // The other reports are not under this cap.
        report(g, "kpis", PERIOD).andExpect(status().isOk()).andExpect(jsonPath("$.deliveredOrders").value(3));
    }
}
