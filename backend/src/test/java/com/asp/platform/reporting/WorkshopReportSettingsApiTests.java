package com.asp.platform.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/// `labourCostPerHourCents` and `retentionMonths` on `GET/PUT /api/v1/workshop/settings` (plan 08).
class WorkshopReportSettingsApiTests extends ReportTestSupport {

    private static final String BASE = """
            "name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,"labourRateCents":4500""";

    private ResultActions save(Shop g, String extra, long version) throws Exception {
        return call(put("/api/v1/workshop/settings")
                .content(withVersion("{" + BASE + (extra.isEmpty() ? "" : "," + extra) + "}", version)), g.owner());
    }

    private ResultActions save(Shop g, String extra) throws Exception {
        return save(g, extra, versionAt(g.owner(), "/api/v1/workshop/settings"));
    }

    @Test
    void should_default_to_no_labour_cost_and_twelve_months() throws Exception {
        Shop g = garage("Oficina Definicoes Novas");

        call(get("/api/v1/workshop/settings"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.retentionMonths").value(12));
    }

    @Test
    void should_save_both_settings_and_let_the_front_desk_read_them() throws Exception {
        Shop g = garage("Oficina Definicoes Gravadas");

        save(g, "\"labourCostPerHourCents\":2500,\"retentionMonths\":18").andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(2500))
                .andExpect(jsonPath("$.retentionMonths").value(18))
                .andExpect(jsonPath("$.labourRateCents").value(4500));

        // The labour cost per hour is a cost, not a price: the owner sees it, the front desk gets null.
        call(get("/api/v1/workshop/settings"), member(g, "FRONT_DESK")).andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.retentionMonths").value(18))
                .andExpect(jsonPath("$.labourRateCents").value(4500));
        call(get("/api/v1/workshop/settings"), g.owner())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(2500));
    }

    @Test
    void should_keep_the_stored_values_when_a_request_leaves_them_out() throws Exception {
        Shop g = garage("Oficina Definicoes Mantidas");
        save(g, "\"labourCostPerHourCents\":2500,\"retentionMonths\":18").andExpect(status().isOk());

        save(g, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(2500))
                .andExpect(jsonPath("$.retentionMonths").value(18));
    }

    @Test
    void should_clear_the_labour_cost_on_request_and_accept_zero_as_a_value() throws Exception {
        Shop g = garage("Oficina Definicoes Limpas");
        save(g, "\"labourCostPerHourCents\":0").andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(0));

        save(g, "\"clearLabourCost\":true").andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.clearLabourCost").doesNotExist());
        call(get("/api/v1/workshop/settings"), g.owner())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void should_refuse_a_value_together_with_clearing_it() throws Exception {
        Shop g = garage("Oficina Definicoes Ambiguas");

        save(g, "\"labourCostPerHourCents\":100,\"clearLabourCost\":true").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.labourCostPerHourCents").exists());
    }

    @Test
    void should_refuse_values_out_of_range() throws Exception {
        Shop g = garage("Oficina Definicoes Invalidas");

        save(g, "\"labourCostPerHourCents\":-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.labourCostPerHourCents").exists());
        save(g, "\"labourCostPerHourCents\":1000001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.labourCostPerHourCents").exists());
        save(g, "\"labourCostPerHourCents\":1000000").andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(1000000));
        save(g, "\"retentionMonths\":0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.retentionMonths").exists());
        save(g, "\"retentionMonths\":61").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.retentionMonths").exists());
        save(g, "\"retentionMonths\":1").andExpect(status().isOk());
        save(g, "\"retentionMonths\":60").andExpect(status().isOk());
    }

    @Test
    void should_answer_a_stale_version_with_409_and_change_nothing() throws Exception {
        Shop g = garage("Oficina Definicoes Versao");
        long version = versionAt(g.owner(), "/api/v1/workshop/settings");
        save(g, "\"labourCostPerHourCents\":2500", version).andExpect(status().isOk());

        save(g, "\"labourCostPerHourCents\":9999", version).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(get("/api/v1/workshop/settings"), g.owner())
                .andExpect(jsonPath("$.labourCostPerHourCents").value(2500));
    }

    @Test
    void should_let_only_the_owner_write_them() throws Exception {
        Shop g = garage("Oficina Definicoes Dono");

        call(put("/api/v1/workshop/settings").content(withVersion("{" + BASE + ",\"labourCostPerHourCents\":1}", 0)),
                member(g, "FRONT_DESK")).andExpect(status().isForbidden());
    }
}
