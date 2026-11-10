package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.asp.platform.billing.infrastructure.SandboxScenario.Fault;

/// InvoiceIssued is published when the provider issues an invoice or a credit note, and only then.
@RecordApplicationEvents
class BillingEventsTests extends BillingTestSupport {

    @Autowired
    ApplicationEvents events;

    @Test
    void should_publish_invoice_issued_for_an_invoice_and_a_credit_note_and_not_for_failed_or_discarded()
            throws Exception {
        Shop g = garage("Eventos Faturas");
        String invoice = issuedInvoice(g);
        assertThat(events.stream(InvoiceIssued.class)).singleElement().satisfies(e -> {
            assertThat(e.invoiceId().toString()).isEqualTo(invoice);
            assertThat(e.number()).isEqualTo("FT SANDBOX/1");
            assertThat(e.totalCents()).isEqualTo(TOTAL);
        });

        call(post("/api/v1/invoices/" + invoice + "/credit-notes")
                .content("{\"requestId\":\"" + newRequestId() + "\",\"reason\":\"Erro\"}"), g.owner())
                .andExpect(status().isCreated());
        assertThat(events.stream(InvoiceIssued.class).map(InvoiceIssued::number)).containsExactly("FT SANDBOX/1",
                "NC SANDBOX/1");

        // A refused document, then discarded: nothing is announced.
        scenario.failInvoicing(Fault.REJECT);
        String failed = idOf(issue(g.owner(), delivered(g), newRequestId()).andExpect(status().isAccepted()));
        call(post("/api/v1/invoices/" + failed + "/discard").content("{\"reason\":\"Errado\",\"version\":"
                + invoiceVersion(g, failed) + "}"), g.owner()).andExpect(status().isOk());
        assertThat(events.stream(InvoiceIssued.class)).hasSize(2);
    }
}
