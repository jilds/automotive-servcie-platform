package com.asp.platform.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/// Mechanics never see prices or customer data, so every billing endpoint is closed to them; anonymous callers and
/// platform admins get nothing either.
class BillingAccessTests extends BillingTestSupport {

    private static List<MockHttpServletRequestBuilder> requests() {
        String id = UUID.randomUUID().toString();
        String body = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"reason\":\"x\",\"version\":0,"
                + "\"method\":\"CASH\",\"outcome\":\"PAID\"}";
        return List.of(
                get("/api/v1/billing/settings"),
                post("/api/v1/work-orders/" + id + "/invoices").content(body),
                get("/api/v1/work-orders/" + id + "/invoices"),
                get("/api/v1/invoices"),
                get("/api/v1/invoices/" + id),
                get("/api/v1/invoices/" + id + "/pdf"),
                post("/api/v1/invoices/" + id + "/retry"),
                post("/api/v1/invoices/" + id + "/discard").content(body),
                post("/api/v1/invoices/" + id + "/void").content(body),
                post("/api/v1/invoices/" + id + "/credit-notes").content(body),
                post("/api/v1/invoices/" + id + "/payments").content(body),
                get("/api/v1/payments/" + id),
                post("/api/v1/payments/" + id + "/refresh"),
                post("/api/v1/payments/" + id + "/cancel").content(body),
                post("/api/v1/payments/" + id + "/sandbox/simulate").content(body));
    }

    @Test
    void should_close_every_billing_endpoint_to_mechanics() throws Exception {
        Shop g = garage("Acesso Faturas Um");
        String mechanic = member(g, "MECHANIC");

        for (MockHttpServletRequestBuilder request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer " + mechanic).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
        // A mechanic cannot even reach an invoice that exists.
        String invoice = issuedInvoice(g);
        call(get("/api/v1/invoices/" + invoice), mechanic).andExpect(status().isForbidden());
    }

    @Test
    void should_require_a_token_and_refuse_platform_admins() throws Exception {
        Shop g = garage("Acesso Faturas Dois");
        String admin = platformAdmin();

        for (MockHttpServletRequestBuilder request : requests()) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
        call(get("/api/v1/invoices"), member(g, "FRONT_DESK")).andExpect(status().isOk());
    }
}
