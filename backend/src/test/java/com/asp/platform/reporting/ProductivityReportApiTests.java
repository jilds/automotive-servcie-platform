package com.asp.platform.reporting;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/// `GET /api/v1/reports/productivity` on the fixed workshop of [ReportTestSupport#seedScenario()] and on workshops in
/// other time zones for the day boundaries.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductivityReportApiTests extends ReportTestSupport {

    private Scenario s;

    @BeforeAll
    void seed() throws Exception {
        s = seedScenario();
    }

    @Test
    void should_report_each_mechanic_with_activity_ordered_by_name_and_the_totals() throws Exception {
        report(s.shop(), "productivity", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.mechanics", hasSize(3)))
                // Rui: entries that started in the period 3.5 + 2 + 1 + 0.5 (the ones on 31 Oct and 1 Dec are out).
                .andExpect(jsonPath("$.mechanics[0].mechanicId").value(s.rui().id()))
                .andExpect(jsonPath("$.mechanics[0].name").value("Rui"))
                .andExpect(jsonPath("$.mechanics[0].active").value(true))
                .andExpect(jsonPath("$.mechanics[0].trackedHours").value(7.0))
                .andExpect(jsonPath("$.mechanics[0].jobs").value(2))
                .andExpect(jsonPath("$.mechanics[0].standardHours").value(3.5))
                .andExpect(jsonPath("$.mechanics[0].trackedHoursOnJobs").value(5.5))
                .andExpect(jsonPath("$.mechanics[0].efficiency").value(0.64))
                .andExpect(jsonPath("$.mechanics[0].averageHoursInWorkshop").value(42.5))
                // Teresa: one delivered job without tracked time (her running timer counts nothing).
                .andExpect(jsonPath("$.mechanics[1].name").value("Teresa"))
                .andExpect(jsonPath("$.mechanics[1].trackedHours").value(0.0))
                .andExpect(jsonPath("$.mechanics[1].jobs").value(1))
                .andExpect(jsonPath("$.mechanics[1].standardHours").value(0.0))
                .andExpect(jsonPath("$.mechanics[1].trackedHoursOnJobs").value(0.0))
                .andExpect(jsonPath("$.mechanics[1].efficiency").doesNotExist())
                .andExpect(jsonPath("$.mechanics[1].averageHoursInWorkshop").value(4.0))
                // Ze: tracked time but no standard time on his jobs: efficiency 0, not null.
                .andExpect(jsonPath("$.mechanics[2].name").value("Ze"))
                .andExpect(jsonPath("$.mechanics[2].trackedHours").value(1.0))
                .andExpect(jsonPath("$.mechanics[2].jobs").value(2))
                .andExpect(jsonPath("$.mechanics[2].standardHours").value(0.0))
                .andExpect(jsonPath("$.mechanics[2].trackedHoursOnJobs").value(1.0))
                .andExpect(jsonPath("$.mechanics[2].efficiency").value(0.0))
                .andExpect(jsonPath("$.mechanics[2].averageHoursInWorkshop").value(25.5))
                .andExpect(jsonPath("$.totals.trackedHours").value(8.0))
                .andExpect(jsonPath("$.totals.jobs").value(5))
                .andExpect(jsonPath("$.totals.standardHours").value(3.5))
                .andExpect(jsonPath("$.totals.trackedHoursOnJobs").value(6.5))
                .andExpect(jsonPath("$.totals.efficiency").value(0.54))
                .andExpect(jsonPath("$.totals.averageHoursInWorkshop").value(28.0));
    }

    @Test
    void should_filter_by_mechanic_and_recompute_the_totals() throws Exception {
        report(s.shop(), "productivity", PERIOD + "&mechanicId=" + s.ze().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(1)))
                .andExpect(jsonPath("$.mechanics[0].name").value("Ze"))
                .andExpect(jsonPath("$.totals.jobs").value(2))
                .andExpect(jsonPath("$.totals.trackedHours").value(1.0))
                .andExpect(jsonPath("$.totals.averageHoursInWorkshop").value(25.5));
    }

    @Test
    void should_list_a_filtered_mechanic_without_activity_with_zeros() throws Exception {
        report(s.shop(), "productivity", "from=2026-05-01&to=2026-05-31&mechanicId=" + s.ze().id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(1)))
                .andExpect(jsonPath("$.mechanics[0].name").value("Ze"))
                .andExpect(jsonPath("$.mechanics[0].trackedHours").value(0.0))
                .andExpect(jsonPath("$.mechanics[0].jobs").value(0))
                .andExpect(jsonPath("$.mechanics[0].efficiency").doesNotExist())
                .andExpect(jsonPath("$.mechanics[0].averageHoursInWorkshop").doesNotExist());
    }

    @Test
    void should_match_nothing_for_a_mechanic_of_another_workshop_or_unknown() throws Exception {
        Shop other = garage("Outra Oficina Prod " + UUID.randomUUID());
        Mechanic stranger = mechanic(other);

        report(s.shop(), "productivity", PERIOD + "&mechanicId=" + stranger.id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)));
        report(s.shop(), "productivity", PERIOD + "&mechanicId=" + UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)));
    }

    @Test
    void should_return_empty_zeros_for_a_period_without_activity() throws Exception {
        report(s.shop(), "productivity", "from=2026-05-01&to=2026-05-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)))
                .andExpect(jsonPath("$.totals.trackedHours").value(0.0))
                .andExpect(jsonPath("$.totals.jobs").value(0))
                .andExpect(jsonPath("$.totals.efficiency").doesNotExist())
                .andExpect(jsonPath("$.totals.averageHoursInWorkshop").doesNotExist());
    }

    @Test
    void should_limit_the_period_to_24_months_and_check_the_mechanic_id() throws Exception {
        report(s.shop(), "productivity", "from=2025-01-01&to=2027-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("report-range-too-large"));
        report(s.shop(), "productivity", PERIOD + "&mechanicId=x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mechanicId").exists());
    }

    @Test
    void should_cut_the_period_into_local_days_across_the_spring_clock_change_in_lisbon() throws Exception {
        Shop g = garage("Oficina Hora de Verao " + UUID.randomUUID());
        Mechanic m = mechanicNamed(g, "Ines");
        String wo = workOrder(g, vehicle(g, false), m.id());
        labour(g, wo, "Servico", 1000, 23);
        deliver(g, wo);
        // 23:30Z on 29 March is 00:30 on the 30th in Lisbon (summer time since 01:00Z).
        retime(g, wo, "2026-03-29T10:00:00Z", "2026-03-29T23:30:00Z");
        // The mechanic started at 23:30Z on the 28th (local 23:30 on the 28th, still winter time).
        entry(g, wo, m.id(), null, "2026-03-28T23:30:00Z", "2026-03-29T00:30:00Z");

        report(g, "productivity", "from=2026-03-29&to=2026-03-29").andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)));
        report(g, "productivity", "from=2026-03-30&to=2026-03-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(1)))
                .andExpect(jsonPath("$.mechanics[0].jobs").value(1))
                .andExpect(jsonPath("$.mechanics[0].trackedHours").value(0.0));
        report(g, "productivity", "from=2026-03-28&to=2026-03-28").andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(1)))
                .andExpect(jsonPath("$.mechanics[0].jobs").value(0))
                .andExpect(jsonPath("$.mechanics[0].trackedHours").value(1.0));
    }

    @Test
    void should_cut_the_period_into_local_days_in_sao_paulo() throws Exception {
        Shop g = garage("Oficina Sao Paulo " + UUID.randomUUID());
        saveSettings(g, "BR", "America/Sao_Paulo", "");
        Mechanic m = mechanicNamed(g, "Joao");
        String wo = workOrder(g, vehicle(g, false), m.id());
        labour(g, wo, "Servico", 1000, 23);
        deliver(g, wo);
        // 02:30Z on 10 June is 23:30 on the 9th in Sao Paulo (UTC-3).
        retime(g, wo, "2026-06-09T12:00:00Z", "2026-06-10T02:30:00Z");

        report(g, "productivity", "from=2026-06-09&to=2026-06-09").andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.mechanics[0].jobs").value(1));
        report(g, "productivity", "from=2026-06-10&to=2026-06-10").andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanics", hasSize(0)));
        report(g, "cost-vs-quote", "from=2026-06-09&to=2026-06-09").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].deliveredAt").value("2026-06-10T02:30:00Z"));
        report(g, "cost-vs-quote", "from=2026-06-10&to=2026-06-10").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
