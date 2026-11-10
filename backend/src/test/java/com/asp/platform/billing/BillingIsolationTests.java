package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// Row-level security on every table of V20 and V21 (ADR 0002), and the API answering 404 for another workshop rows.
class BillingIsolationTests extends BillingTestSupport {

    private static final List<String> TABLES = List.of("invoice", "invoice_line", "payment", "payment_event",
            "invoicing_sandbox_document");

    @Test
    void should_hide_every_billing_table_from_another_workshop() throws Exception {
        Shop a = garage("Isolar Faturas A");
        Shop b = garage("Isolar Faturas B");
        String id = issuedInvoice(a);
        String reference = multibanco(a, id, 2000);
        signedWebhook(a, webhookBody("evt-iso", providerPaymentId(reference), "PAID", 2000));

        for (String table : TABLES) {
            assertThat(count(a, "select count(*) from " + table)).as("%s seen by its workshop", table).isPositive();
            assertThat(count(b, "select count(*) from " + table)).as("%s seen by another workshop", table).isZero();
            assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class))
                    .as("%s without a workshop", table).isZero();
        }
    }

    @Test
    void should_refuse_a_row_written_for_another_workshop() throws Exception {
        Shop a = garage("Isolar Escrita A");
        Shop b = garage("Isolar Escrita B");

        assertThatThrownBy(() -> asWorkshop(b, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    insert into payment_event (id, tenant_id, provider, event_id, outcome, received_at)
                    values (?::uuid, ?::uuid, 'sandbox', 'evt-x', 'IGNORED', now())""")) {
                s.setString(1, UUID.randomUUID().toString());
                s.setString(2, a.id());
                return s.executeUpdate();
            }
        })).rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void should_answer_404_to_another_workshop_on_every_endpoint() throws Exception {
        Shop a = garage("Isolar Api A");
        Shop b = garage("Isolar Api B");
        String wo = delivered(a);
        String id = issued(a, wo);
        String payment = multibanco(a, id, 2000);

        call(get("/api/v1/invoices/" + id), b.owner()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("invoice-not-found"));
        call(get("/api/v1/invoices/" + id + "/pdf"), b.owner()).andExpect(status().isNotFound());
        call(post("/api/v1/invoices/" + id + "/retry"), b.owner()).andExpect(status().isNotFound());
        call(post("/api/v1/invoices/" + id + "/void").content("{\"reason\":\"x\",\"version\":1}"), b.owner())
                .andExpect(status().isNotFound());
        call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\"x\",\"version\":1}"), b.owner())
                .andExpect(status().isNotFound());
        call(post("/api/v1/invoices/" + id + "/credit-notes")
                .content("{\"requestId\":\"" + newRequestId() + "\",\"reason\":\"x\"}"), b.owner())
                .andExpect(status().isNotFound());
        pay(b, id, "{\"method\":\"CASH\",\"amountCents\":100}").andExpect(status().isNotFound());
        call(get("/api/v1/payments/" + payment), b.owner()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment-not-found"));
        call(post("/api/v1/payments/" + payment + "/refresh"), b.owner()).andExpect(status().isNotFound());
        call(post("/api/v1/payments/" + payment + "/cancel").content("{\"version\":0}"), b.owner())
                .andExpect(status().isNotFound());
        call(post("/api/v1/payments/" + payment + "/sandbox/simulate").content("{\"outcome\":\"PAID\"}"), b.owner())
                .andExpect(status().isNotFound());
        issue(b.owner(), wo, newRequestId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(get("/api/v1/work-orders/" + wo + "/invoices"), b.owner()).andExpect(status().isNotFound());
        call(get("/api/v1/invoices"), b.owner()).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        assertThat(paymentStatus(a, payment)).isEqualTo("PENDING");
    }
}
