package com.asp.platform.reporting;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// The retention rate looks forward (owner decision 2026-11-10): a customer with a delivered order in the period is
/// retained when they have ANOTHER delivered order on a LATER local day within `retentionMonths` after it. Same-day
/// orders never count. A period whose follow-up window is not over yet answers `final: false`.
class RetentionKpiApiTests extends ReportTestSupport {

    private static final String P = "from=2024-03-01&to=2024-03-31";

    private String plateOf(int n) {
        return "RT-%02d-AA".formatted(n);
    }

    /// A delivered order of the vehicle, checked in and delivered at noon UTC on these days (Lisbon: same local day).
    private void delivered(Shop g, String vehicle, String day) throws Exception {
        String wo = workOrder(g, vehicle, null);
        labour(g, wo, "Servico", 1000, 23);
        deliver(g, wo);
        retime(g, wo, day + "T10:00:00Z", day + "T12:00:00Z");
    }

    @Test
    void should_count_forward_follow_ups_only_on_a_later_day_within_the_window() throws Exception {
        Shop g = garage("Oficina Retencao " + UUID.randomUUID());
        // A: a fleet, two vehicles delivered the same day, nothing else: same-day orders never count.
        String a = createCustomer(g.owner(), "Frota");
        delivered(g, createVehicle(g.owner(), a, plateOf(1)), "2024-03-10");
        delivered(g, createVehicle(g.owner(), a, plateOf(2)), "2024-03-10");
        // B: exactly 12 months later (a later day, on the last day of the window): retained.
        String b = createCustomer(g.owner(), "Exato");
        String bv = createVehicle(g.owner(), b, plateOf(3));
        delivered(g, bv, "2024-03-15");
        delivered(g, bv, "2025-03-15");
        // C: one day beyond the window: not retained.
        String c = createCustomer(g.owner(), "Tarde");
        String cv = createVehicle(g.owner(), c, plateOf(4));
        delivered(g, cv, "2024-03-15");
        delivered(g, cv, "2025-03-16");
        // D: back with another vehicle of the same customer: retained.
        String d = createCustomer(g.owner(), "Dois Carros");
        delivered(g, createVehicle(g.owner(), d, plateOf(5)), "2024-03-05");
        delivered(g, createVehicle(g.owner(), d, plateOf(6)), "2024-08-01");
        // E: only an EARLIER order: forward-looking, so not retained.
        String e = createCustomer(g.owner(), "Antes");
        String ev = createVehicle(g.owner(), e, plateOf(7));
        delivered(g, ev, "2024-01-10");
        delivered(g, ev, "2024-03-20");
        // F: two orders in the period on different days: the later one is the follow-up of the first.
        String f = createCustomer(g.owner(), "Duas Vezes");
        String fv = createVehicle(g.owner(), f, plateOf(8));
        delivered(g, fv, "2024-03-02");
        delivered(g, fv, "2024-03-25");

        report(g, "kpis", P).andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.available").value(true))
                .andExpect(jsonPath("$.retention.months").value(12))
                .andExpect(jsonPath("$.retention.customers").value(6))
                .andExpect(jsonPath("$.retention.retained").value(3))
                .andExpect(jsonPath("$.retention.ratePercent").value(50.0))
                .andExpect(jsonPath("$.retention.final").value(true))
                .andExpect(jsonPath("$.retention.monthsCovered").value(12));
    }

    @Test
    void should_use_the_local_day_of_the_workshop_zone_for_same_day_and_window_edges() throws Exception {
        Shop g = garage("Oficina Retencao Zona " + UUID.randomUUID());
        String a = createCustomer(g.owner(), "Meia Noite");
        String av = createVehicle(g.owner(), a, plateOf(21));
        // 23:30Z on 10 July 2024 is 00:30 on the 11th in Lisbon (summer time). A second order at
        // 00:15Z on the 11th is the same local day: not a follow-up (by UTC days it would be).
        String wo1 = workOrder(g, av, null);
        labour(g, wo1, "Servico", 1000, 23);
        deliver(g, wo1);
        retime(g, wo1, "2024-07-10T20:00:00Z", "2024-07-10T23:30:00Z");
        String wo2 = workOrder(g, av, null);
        labour(g, wo2, "Servico", 1000, 23);
        deliver(g, wo2);
        retime(g, wo2, "2024-07-11T00:00:00Z", "2024-07-11T00:15:00Z");

        report(g, "kpis", "from=2024-07-11&to=2024-07-11").andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.customers").value(1))
                .andExpect(jsonPath("$.retention.retained").value(0));
    }

    @Test
    void should_use_the_retention_window_of_the_settings_and_report_whether_the_window_is_complete() throws Exception {
        Shop g = garage("Oficina Retencao Janela " + UUID.randomUUID());
        saveSettings(g, "PT", "Europe/Lisbon", "\"retentionMonths\":3");
        String a = createCustomer(g.owner(), "Tres Meses");
        String av = createVehicle(g.owner(), a, plateOf(31));
        delivered(g, av, "2024-03-15");
        delivered(g, av, "2024-06-15");
        String b = createCustomer(g.owner(), "Quatro Meses");
        String bv = createVehicle(g.owner(), b, plateOf(32));
        delivered(g, bv, "2024-03-15");
        delivered(g, bv, "2024-07-15");

        report(g, "kpis", P).andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.months").value(3))
                .andExpect(jsonPath("$.retention.customers").value(2))
                .andExpect(jsonPath("$.retention.retained").value(1))
                .andExpect(jsonPath("$.retention.ratePercent").value(50.0));
    }

    @Test
    void should_say_the_rate_is_not_final_while_the_follow_up_window_is_still_open() throws Exception {
        Shop g = garage("Oficina Retencao Aberta " + UUID.randomUUID());
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Lisbon"));

        // Period ended today: no follow-up month has passed.
        report(g, "kpis", "from=" + today.withDayOfMonth(1) + "&to=" + today).andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.final").value(false))
                .andExpect(jsonPath("$.retention.monthsCovered").value(0));
        // Period ended three months ago: three of the twelve months are covered.
        LocalDate end = today.minusMonths(3);
        report(g, "kpis", "from=" + end.withDayOfMonth(1) + "&to=" + end).andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.final").value(false))
                .andExpect(jsonPath("$.retention.monthsCovered").value(3));
        // Period ended exactly twelve months ago: complete.
        LocalDate old = today.minusMonths(12);
        report(g, "kpis", "from=" + old.withDayOfMonth(1) + "&to=" + old).andExpect(status().isOk())
                .andExpect(jsonPath("$.retention.final").value(true))
                .andExpect(jsonPath("$.retention.monthsCovered").value(12));
    }
}
