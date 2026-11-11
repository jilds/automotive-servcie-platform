package com.asp.platform.reporting;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/// A second workshop never sees the first one's orders, hours, invoices or customers in any report, and its own
/// numbers do not leak into the first.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportIsolationTests extends ReportTestSupport {

    private Scenario s;
    private Shop other;

    @BeforeAll
    void seed() throws Exception {
        s = seedScenario();
        other = garage("Oficina Vizinha");
    }

    @Test
    void should_show_a_workshop_without_work_nothing_of_another_workshop() throws Exception {
        Shop other = garage("Oficina Vazia " + java.util.UUID.randomUUID());
        report(other, "cost-vs-quote", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orders").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        report(other, "productivity", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)))
                .andExpect(jsonPath("$.totals.jobs").value(0));
        report(other, "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredOrders").value(0))
                .andExpect(jsonPath("$.averageOrderValue.invoicedOrders").value(0))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.orders").value(0))
                .andExpect(jsonPath("$.margin.netCents").value(0))
                .andExpect(jsonPath("$.margin.partsCostCents").value(0))
                .andExpect(jsonPath("$.margin.byService", hasSize(0)))
                .andExpect(jsonPath("$.averageTimePerJob.byService", hasSize(0)))
                .andExpect(jsonPath("$.retention.customers").value(0))
                .andExpect(jsonPath("$.comebacks.orders").value(0))
                .andExpect(jsonPath("$.labourCostSet").value(false));
    }

    @Test
    void should_not_find_the_other_workshops_mechanics_or_names() throws Exception {
        report(other, "productivity", PERIOD + "&mechanicId=" + s.rui().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)));
        report(other, "cost-vs-quote", PERIOD + "&mechanicId=" + s.rui().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void should_not_mix_the_other_workshops_work_into_the_first() throws Exception {
        String wo = delivered(other);
        entry(other, wo, mechanic(other).id(), null, "2026-11-10T08:00:00Z", "2026-11-10T20:00:00Z");
        retime(other, wo, "2026-11-10T07:00:00Z", "2026-11-11T10:00:00Z");
        issued(other, wo);

        report(other, "cost-vs-quote", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orders").value(1));
        report(s.shop(), "cost-vs-quote", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orders").value(5))
                .andExpect(jsonPath("$.summary.trackedHours").value(6.5));
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageOrderValue.invoicedOrders").value(2))
                .andExpect(jsonPath("$.averageOrderValue.netCents").value(11500));
        report(s.shop(), "productivity", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(3)))
                .andExpect(jsonPath("$.totals.trackedHours").value(8.0));
    }
}
