package com.asp.platform.reporting;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/// A comeback: an order of the period whose vehicle had another order DELIVERED before this one was checked in, at most
/// 30 x 24 hours before (edges included).
class ComebackKpiApiTests extends ReportTestSupport {

    private void order(Shop g, String vehicle, String checkedIn, String deliveredAt) throws Exception {
        String wo = workOrder(g, vehicle, null);
        labour(g, wo, "Servico", 1000, 23);
        deliver(g, wo);
        retime(g, wo, checkedIn, deliveredAt);
    }

    @Test
    void should_count_only_a_return_within_30_days_of_the_last_delivery_of_the_same_vehicle() throws Exception {
        Shop g = garage("Oficina Regressos " + UUID.randomUUID());
        String customer = createCustomer(g.owner(), "Cliente");

        // Vehicle A: delivered 1 Jan 12:00; checked in again exactly 720 hours later: a comeback (the edge counts).
        String a = createVehicle(g.owner(), customer, "CB-01-AA");
        order(g, a, "2026-01-01T08:00:00Z", "2026-01-01T12:00:00Z");
        order(g, a, "2026-01-31T12:00:00Z", "2026-02-01T12:00:00Z");
        // Vehicle B: delivered 1 Mar 12:00; checked in 30 days and one minute later: not a comeback.
        String b = createVehicle(g.owner(), customer, "CB-02-AA");
        order(g, b, "2026-03-01T08:00:00Z", "2026-03-01T12:00:00Z");
        order(g, b, "2026-03-31T12:01:00Z", "2026-04-01T12:00:00Z");
        // Another vehicle of the same customer, delivered a day before: not a comeback (it is the vehicle that returns).
        String c = createVehicle(g.owner(), customer, "CB-03-AA");
        order(g, c, "2026-01-05T08:00:00Z", "2026-01-10T12:00:00Z");
        // Vehicle D: the earlier order was delivered AFTER this one was checked in (overlapping stays): neither is a
        // comeback of the other.
        String d = createVehicle(g.owner(), customer, "CB-04-AA");
        order(g, d, "2026-05-01T08:00:00Z", "2026-05-02T12:00:00Z");
        order(g, d, "2026-05-02T08:00:00Z", "2026-05-03T12:00:00Z");

        // Delivered in the period: A1, A2, B1, B2, C, D1, D2 = 7; one comeback (A2).
        report(g, "kpis", "from=2026-01-01&to=2026-05-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredOrders").value(7))
                .andExpect(jsonPath("$.comebacks.days").value(30))
                .andExpect(jsonPath("$.comebacks.orders").value(1))
                .andExpect(jsonPath("$.comebacks.ratePercent").value(14.29));
    }
}
