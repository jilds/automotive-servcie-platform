package com.asp.platform.reporting;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.asp.platform.billing.infrastructure.SandboxScenario;

/// `GET /api/v1/reports/kpis` on the fixed workshop of [ReportTestSupport#seedScenario()].
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KpiReportApiTests extends ReportTestSupport {

    private Scenario s;

    @BeforeAll
    void seed() throws Exception {
        s = seedScenario();
    }

    @Test
    void should_answer_the_envelope_and_the_kpis_without_data_as_unavailable() throws Exception {
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.labourCostSet").value(true))
                .andExpect(jsonPath("$.retentionMonths").value(12))
                .andExpect(jsonPath("$.deliveredOrders").value(5))
                .andExpect(jsonPath("$.occupancy.available").value(false))
                .andExpect(jsonPath("$.occupancy.availableWith").value("plan-12-bays"))
                .andExpect(jsonPath("$.occupancy.*", hasSize(2)))
                .andExpect(jsonPath("$.satisfaction.available").value(false))
                .andExpect(jsonPath("$.satisfaction.availableWith").value("satisfaction"))
                .andExpect(jsonPath("$.satisfaction.*", hasSize(2)))
                .andExpect(jsonPath("$.complaints.available").value(false))
                .andExpect(jsonPath("$.complaints.availableWith").value("complaints"))
                .andExpect(jsonPath("$.complaints.*", hasSize(2)));
    }

    @Test
    void should_average_the_tracked_time_per_job_and_per_service() throws Exception {
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageTimePerJob.available").value(true))
                // Tracked on the 5 delivered orders: 3.5 + 2 + 1 = 6.5 h on 3 orders with time.
                .andExpect(jsonPath("$.averageTimePerJob.trackedHours").value(6.5))
                .andExpect(jsonPath("$.averageTimePerJob.ordersWithTime").value(3))
                .andExpect(jsonPath("$.averageTimePerJob.hoursPerJob").value(2.17))
                .andExpect(jsonPath("$.averageTimePerJob.byService", hasSize(2)))
                .andExpect(jsonPath("$.averageTimePerJob.byService[0].serviceItemId").value(s.s1()))
                .andExpect(jsonPath("$.averageTimePerJob.byService[0].name").value("Mudanca oleo"))
                .andExpect(jsonPath("$.averageTimePerJob.byService[0].trackedHours").value(2.0))
                .andExpect(jsonPath("$.averageTimePerJob.byService[0].orders").value(1))
                .andExpect(jsonPath("$.averageTimePerJob.byService[0].hoursPerJob").value(2.0))
                .andExpect(jsonPath("$.averageTimePerJob.byService[1].serviceItemId").value(s.s2()))
                .andExpect(jsonPath("$.averageTimePerJob.byService[1].name").value("Travoes"));
    }

    @Test
    void should_value_orders_from_issued_invoices_minus_credit_notes_and_show_the_rest_apart() throws Exception {
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageOrderValue.available").value(true))
                .andExpect(jsonPath("$.averageOrderValue.source").value("ISSUED_INVOICES_MINUS_CREDIT_NOTES"))
                // o1 (6500 / 7995) and o3 (5000 / 6150); o5 is fully credited, o2's invoice was voided, o4 has none.
                .andExpect(jsonPath("$.averageOrderValue.invoicedOrders").value(2))
                .andExpect(jsonPath("$.averageOrderValue.creditedOrders").value(1))
                .andExpect(jsonPath("$.averageOrderValue.netCents").value(11500))
                .andExpect(jsonPath("$.averageOrderValue.totalCents").value(14145))
                .andExpect(jsonPath("$.averageOrderValue.averageNetCents").value(5750))
                .andExpect(jsonPath("$.averageOrderValue.averageTotalCents").value(7073))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.orders").value(2))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.netCents").value(11000))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.totalCents").value(13530));
    }

    @Test
    void should_keep_the_full_value_of_an_issued_invoice_without_a_credit_note() throws Exception {
        Shop g = garage("Oficina Fatura Simples " + UUID.randomUUID());
        String wo = delivered(g);
        issued(g, wo);

        report(g, "kpis", "from=" + today().minusDays(1) + "&to=" + today().plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageOrderValue.invoicedOrders").value(1))
                .andExpect(jsonPath("$.averageOrderValue.creditedOrders").value(0))
                .andExpect(jsonPath("$.averageOrderValue.netCents").value(6250))
                .andExpect(jsonPath("$.averageOrderValue.totalCents").value(7688))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.orders").value(0))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.netCents").value(0));
    }

    @Test
    void should_not_count_pending_or_failed_invoices_as_revenue() throws Exception {
        Shop g = garage("Oficina Fatura Pendente " + UUID.randomUUID());
        String wo = delivered(g);
        // The provider refuses: the document is stored as FAILED and never counts.
        scenario.failInvoicing(SandboxScenario.Fault.REJECT);
        issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted());

        report(g, "kpis", "from=" + today().minusDays(1) + "&to=" + today().plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageOrderValue.invoicedOrders").value(0))
                .andExpect(jsonPath("$.averageOrderValue.netCents").value(0))
                .andExpect(jsonPath("$.averageOrderValue.averageNetCents").doesNotExist())
                .andExpect(jsonPath("$.averageOrderValue.averageTotalCents").doesNotExist())
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.orders").value(1))
                .andExpect(jsonPath("$.averageOrderValue.notYetInvoiced.netCents").value(6250));
    }

    @Test
    void should_work_out_the_margin_of_the_period_and_of_each_catalogue_service() throws Exception {
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.margin.available").value(true))
                .andExpect(jsonPath("$.margin.basis").value("PARTS_AND_LABOUR"))
                .andExpect(jsonPath("$.margin.labourCostSet").value(true))
                .andExpect(jsonPath("$.margin.orders").value(5))
                .andExpect(jsonPath("$.margin.netCents").value(23500))
                .andExpect(jsonPath("$.margin.partsCostCents").value(1600))
                .andExpect(jsonPath("$.margin.labourCostCents").value(19500))
                .andExpect(jsonPath("$.margin.marginCents").value(2400))
                .andExpect(jsonPath("$.margin.marginPercent").value(10.21))
                // Per service: labour of the time tracked on its lines only (parts stay in the order margin).
                .andExpect(jsonPath("$.margin.byService", hasSize(2)))
                .andExpect(jsonPath("$.margin.byService[0].serviceItemId").value(s.s2()))
                .andExpect(jsonPath("$.margin.byService[0].name").value("Travoes"))
                .andExpect(jsonPath("$.margin.byService[0].lines").value(1))
                .andExpect(jsonPath("$.margin.byService[0].orders").value(1))
                .andExpect(jsonPath("$.margin.byService[0].netCents").value(9000))
                .andExpect(jsonPath("$.margin.byService[0].trackedHours").value(2.0))
                .andExpect(jsonPath("$.margin.byService[0].labourCostCents").value(6000))
                .andExpect(jsonPath("$.margin.byService[0].marginCents").value(3000))
                .andExpect(jsonPath("$.margin.byService[0].marginPercent").value(33.33))
                .andExpect(jsonPath("$.margin.byService[1].serviceItemId").value(s.s1()))
                .andExpect(jsonPath("$.margin.byService[1].netCents").value(4000))
                .andExpect(jsonPath("$.margin.byService[1].labourCostCents").value(6000))
                .andExpect(jsonPath("$.margin.byService[1].marginCents").value(-2000))
                .andExpect(jsonPath("$.margin.byService[1].marginPercent").value(-50.0));
    }

    @Test
    void should_show_the_margin_as_parts_only_without_a_labour_cost() throws Exception {
        Shop g = garage("Oficina Margem Pecas " + UUID.randomUUID());
        delivered(g);

        report(g, "kpis", "from=" + today().minusDays(1) + "&to=" + today().plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostSet").value(false))
                .andExpect(jsonPath("$.margin.basis").value("PARTS_ONLY"))
                .andExpect(jsonPath("$.margin.labourCostSet").value(false))
                .andExpect(jsonPath("$.margin.labourCostCents").doesNotExist())
                .andExpect(jsonPath("$.margin.netCents").value(6250))
                .andExpect(jsonPath("$.margin.marginCents").value(6250));
    }

    @Test
    void should_count_customers_that_came_back_within_the_retention_window() throws Exception {
        // Forward-looking: C1 has three orders on different days in the period; C2 and C3 only earlier orders (before)
        // or none after, so only C1 is retained.
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.available").value(true))
                .andExpect(jsonPath("$.retention.months").value(12))
                .andExpect(jsonPath("$.retention.customers").value(3))
                .andExpect(jsonPath("$.retention.retained").value(1))
                .andExpect(jsonPath("$.retention.ratePercent").value(33.33))
                .andExpect(jsonPath("$.retention.final").isBoolean())
                .andExpect(jsonPath("$.retention.monthsCovered").isNumber());
    }

    @Test
    void should_use_the_retention_window_of_the_workshop_settings() throws Exception {
        Shop g = garage("Oficina Janela Seis " + UUID.randomUUID());
        saveSettings(g, "PT", "Europe/Lisbon", "\"retentionMonths\":6");
        String exact = createCustomer(g.owner(), "Seis Meses");
        String exactCar = createVehicle(g.owner(), exact, "KW-01-AA");
        String late = createCustomer(g.owner(), "Sete Meses");
        String lateCar = createVehicle(g.owner(), late, "KW-02-AA");
        for (String[] order : new String[][] {{exactCar, "2024-03-15"}, {exactCar, "2024-09-15"},
                {lateCar, "2024-03-15"}, {lateCar, "2024-10-15"}}) {
            String wo = workOrder(g, order[0], null);
            labour(g, wo, "Servico", 1000, 23);
            deliver(g, wo);
            retime(g, wo, order[1] + "T10:00:00Z", order[1] + "T12:00:00Z");
        }

        // Six months: a follow-up exactly six months later counts, one seven months later does not.
        report(g, "kpis", "from=2024-03-01&to=2024-03-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionMonths").value(6))
                .andExpect(jsonPath("$.retention.months").value(6))
                .andExpect(jsonPath("$.retention.customers").value(2))
                .andExpect(jsonPath("$.retention.retained").value(1))
                .andExpect(jsonPath("$.retention.ratePercent").value(50.0));
    }

    @Test
    void should_count_a_comeback_when_the_vehicle_returns_within_30_days_of_its_last_delivery() throws Exception {
        // o5 (V1) was checked in on 20 Nov, 15 days after o1 (V1) was delivered; o2 (V2) came 9 months after o0.
        report(s.shop(), "kpis", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.comebacks.available").value(true))
                .andExpect(jsonPath("$.comebacks.days").value(30))
                .andExpect(jsonPath("$.comebacks.orders").value(1))
                .andExpect(jsonPath("$.comebacks.ratePercent").value(20.0));
    }

    @Test
    void should_return_nulls_and_zeros_for_an_empty_period_but_keep_the_available_flags() throws Exception {
        report(s.shop(), "kpis", "from=2026-05-01&to=2026-05-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredOrders").value(0))
                .andExpect(jsonPath("$.averageTimePerJob.available").value(true))
                .andExpect(jsonPath("$.averageTimePerJob.trackedHours").value(0.0))
                .andExpect(jsonPath("$.averageTimePerJob.hoursPerJob").doesNotExist())
                .andExpect(jsonPath("$.averageTimePerJob.byService", hasSize(0)))
                .andExpect(jsonPath("$.averageOrderValue.invoicedOrders").value(0))
                .andExpect(jsonPath("$.averageOrderValue.averageNetCents").doesNotExist())
                .andExpect(jsonPath("$.margin.orders").value(0))
                .andExpect(jsonPath("$.margin.marginPercent").doesNotExist())
                .andExpect(jsonPath("$.margin.byService", hasSize(0)))
                .andExpect(jsonPath("$.retention.customers").value(0))
                .andExpect(jsonPath("$.retention.ratePercent").doesNotExist())
                .andExpect(jsonPath("$.comebacks.orders").value(0))
                .andExpect(jsonPath("$.comebacks.ratePercent").doesNotExist());
    }

    @Test
    void should_limit_the_period_to_24_months() throws Exception {
        report(s.shop(), "kpis", "from=2025-01-01&to=2027-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("report-range-too-large"));
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Lisbon"));
    }
}
