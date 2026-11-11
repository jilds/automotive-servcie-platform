package com.asp.platform.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

/// Reports show prices, margins and colleagues' hours: the owner only (ADR 0003 for platform admins).
class ReportAccessTests extends ReportTestSupport {

    private static final List<String> ENDPOINTS = List.of("cost-vs-quote", "productivity", "kpis");

    @Test
    void should_answer_the_owner() throws Exception {
        Shop g = garage("Oficina Acesso Dono");

        for (String endpoint : ENDPOINTS) {
            report(g, endpoint, "").andExpect(status().isOk());
        }
    }

    @Test
    void should_refuse_the_front_desk_and_the_mechanic_on_every_report_and_format() throws Exception {
        Shop g = garage("Oficina Acesso Equipa");
        String desk = member(g, "FRONT_DESK");
        String mechanic = member(g, "MECHANIC");

        for (String endpoint : ENDPOINTS) {
            for (String token : List.of(desk, mechanic)) {
                report(token, endpoint, "").andExpect(status().isForbidden());
                report(token, endpoint, "format=csv").andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void should_refuse_a_platform_admin() throws Exception {
        String admin = platformAdmin();

        for (String endpoint : ENDPOINTS) {
            report(admin, endpoint, "").andExpect(status().isForbidden());
        }
    }

    @Test
    void should_refuse_a_request_without_a_token() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mvc.perform(get("/api/v1/reports/" + endpoint)).andExpect(status().isUnauthorized());
        }
    }
}
