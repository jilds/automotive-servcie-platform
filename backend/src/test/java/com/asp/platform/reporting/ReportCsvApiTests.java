package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/// `?format=csv` on every report: what Excel in Portugal opens (BOM, `;`, decimal comma), text that cannot become a
/// formula, quoting. The mechanic's name is `=1+1;"x"` on purpose.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportCsvApiTests extends ReportTestSupport {

    private static final String EVIL_NAME = "=1+1;\"x\"";
    private static final String EVIL_CELL = "\"'=1+1;\"\"x\"\"\"";

    private Shop g;

    @BeforeAll
    void seed() throws Exception {
        g = garage("Oficina Csv");
        saveSettings(g, "PT", "Europe/Lisbon", "\"labourCostPerHourCents\":3000");
        Mechanic m = mechanicNamed(g, EVIL_NAME);
        String service = catalogueService(g, "Revisao;geral", 1000, "1");
        String wo = workOrder(g, vehicle(g, false), m.id());
        String line = addService(g, wo, service);
        deliver(g, wo);
        entry(g, wo, m.id(), line, "2026-11-05T09:00:00Z", "2026-11-05T10:00:00Z");
        retime(g, wo, "2026-11-05T08:00:00Z", "2026-11-05T10:00:00Z");
    }

    @Test
    void should_export_cost_vs_quote_as_excel_friendly_csv() throws Exception {
        MvcResult result = report(g, "cost-vs-quote", PERIOD + "&format=csv").andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv")
                .containsIgnoringCase("charset=UTF-8");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"cost-vs-quote-2026-11-01-2026-11-30.csv\"");
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("﻿"
                + "number;plate;deliveredAt;mechanic;quoteNumber;quoteTotalEur;finalNetEur;finalVatEur;finalTotalEur;"
                + "differenceEur;differencePercent;standardHours;trackedHours;partsCostEur;labourCostEur;marginEur;"
                + "marginPercent\r\n"
                // net 10,00 + VAT 2,30; 1 h standard and tracked; labour 30,00; margin -20,00 = -200,00 %.
                + "1;AA00AA;2026-11-05 10:00;" + EVIL_CELL + ";;;10,00;2,30;12,30;;;1,00;1,00;0,00;30,00;-20,00;"
                + "-200,00\r\n");
    }

    @Test
    void should_export_productivity_as_csv() throws Exception {
        MvcResult result = report(g, "productivity", PERIOD + "&format=csv").andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"productivity-2026-11-01-2026-11-30.csv\"");
        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("﻿"
                + "mechanic;trackedHours;jobs;standardHours;trackedHoursOnJobs;efficiency;averageHoursInWorkshop\r\n"
                + EVIL_CELL + ";1,00;1;1,00;1,00;1,00;2,00\r\n");
    }

    @Test
    void should_export_the_kpis_as_one_row_per_figure() throws Exception {
        MvcResult result = report(g, "kpis", PERIOD + "&format=csv").andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"kpis-2026-11-01-2026-11-30.csv\"");
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿kpi;item;metric;value\r\n");
        assertThat(csv.lines().toList()).contains(
                "﻿kpi;item;metric;value",
                "averageTimePerJob;;trackedHours;1,00",
                "averageTimePerJob;;hoursPerJob;1,00",
                "averageTimePerJob;\"Revisao;geral\";hoursPerJob;1,00",
                "averageOrderValue;;source;ISSUED_INVOICES_MINUS_CREDIT_NOTES",
                "averageOrderValue;;averageNetEur;",
                "notYetInvoiced;;orders;1",
                "notYetInvoiced;;netEur;10,00",
                "margin;;labourCostEur;30,00",
                "margin;;marginEur;-20,00",
                "margin;\"Revisao;geral\";marginEur;-20,00",
                "retention;;customers;1",
                "retention;;ratePercent;0,00",
                "occupancy;;available;false",
                "occupancy;;availableWith;plan-12-bays",
                "satisfaction;;availableWith;satisfaction",
                "complaints;;availableWith;complaints");
        assertThat(csv).doesNotContain("\n\n");
    }

    @Test
    void should_ignore_paging_in_the_csv_and_refuse_a_bad_format() throws Exception {
        MvcResult result = report(g, "cost-vs-quote", PERIOD + "&format=csv&size=1&page=5").andExpect(status().isOk())
                .andReturn();

        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).lines().count())
                .isEqualTo(2);
        report(g, "kpis", PERIOD + "&format=pdf").andExpect(status().isBadRequest());
    }

    @Test
    void should_keep_the_range_limit_for_csv() throws Exception {
        report(g, "cost-vs-quote", "from=2025-01-01&to=2027-01-01&format=csv").andExpect(status().isBadRequest());
    }

    @Test
    void should_return_the_header_only_for_an_empty_period() throws Exception {
        MvcResult result = report(g, "productivity", "from=2026-05-01&to=2026-05-31&format=csv")
                .andExpect(status().isOk()).andReturn();

        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo(
                "﻿mechanic;trackedHours;jobs;standardHours;trackedHoursOnJobs;efficiency;"
                        + "averageHoursInWorkshop\r\n");
    }
}
