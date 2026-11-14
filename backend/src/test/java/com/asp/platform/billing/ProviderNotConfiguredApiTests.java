package com.asp.platform.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/// With no provider configured (the default of the prod profile) nothing is issued and no reference is created, but
/// staff can still record money taken at the counter.
@TestPropertySource(properties = {"app.billing.invoicing.provider=none", "app.billing.payments.provider=none"})
class ProviderNotConfiguredApiTests extends BillingTestSupport {

    @Test
    void should_say_so_in_the_settings_and_refuse_to_issue() throws Exception {
        Shop g = garage("Sem Fornecedor Um");
        String wo = delivered(g);

        call(get("/api/v1/billing/settings"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicing.provider").value("none"))
                .andExpect(jsonPath("$.invoicing.configured").value(false))
                .andExpect(jsonPath("$.invoicing.sandbox").value(false))
                .andExpect(jsonPath("$.payments.configured").value(false))
                .andExpect(jsonPath("$.methods.length()").value(4));
        issue(g.owner(), wo, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("provider-not-configured"));
        call(get("/api/v1/work-orders/" + wo + "/invoices"), g.owner()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_not_be_able_to_simulate_payments_without_the_sandbox() throws Exception {
        Shop g = garage("Sem Fornecedor Dois");

        call(post("/api/v1/payments/" + java.util.UUID.randomUUID() + "/sandbox/simulate")
                .content("{\"outcome\":\"PAID\"}"), g.owner()).andExpect(status().isNotFound());
    }
}
