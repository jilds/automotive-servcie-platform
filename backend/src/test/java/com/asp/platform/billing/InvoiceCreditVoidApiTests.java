package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.billing.infrastructure.SandboxScenario.Fault;

class InvoiceCreditVoidApiTests extends BillingTestSupport {

    private ResultActions voidInvoice(Shop g, String id, long version) throws Exception {
        return call(post("/api/v1/invoices/" + id + "/void").content("{\"reason\":\"Emitida por engano\",\"version\":"
                + version + "}"), g.owner());
    }

    private ResultActions credit(Shop g, String invoiceId, String requestId) throws Exception {
        return call(post("/api/v1/invoices/" + invoiceId + "/credit-notes")
                .content("{\"requestId\":\"%s\",\"reason\":\"Serviço cancelado\"}".formatted(requestId)), g.owner());
    }

    @Test
    void should_void_an_issued_invoice_and_allow_invoicing_the_order_again() throws Exception {
        Shop g = garage("Anular Um");
        String wo = delivered(g);
        String id = issued(g, wo);

        voidInvoice(g, id, invoiceVersion(g, id) + 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        voidInvoice(g, id, invoiceVersion(g, id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED")).andExpect(jsonPath("$.reason").value("Emitida por engano"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty()).andExpect(jsonPath("$.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.number").value("FT SANDBOX/1"));
        // Safe to repeat.
        voidInvoice(g, id, 0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOIDED"));

        issue(g.owner(), wo, newRequestId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("FT SANDBOX/2"));
        call(get("/api/v1/invoices/" + id + "/pdf"), g.owner()).andExpect(status().isOk());
    }

    @Test
    void should_not_void_an_invoice_that_was_paid_but_void_one_with_a_pending_reference() throws Exception {
        Shop g = garage("Anular Dois");
        String paid = issuedInvoice(g);
        cash(g, paid, 1000);
        voidInvoice(g, paid, invoiceVersion(g, paid)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-has-payments"));

        String open = issuedInvoice(g);
        String reference = multibanco(g, open, 0);
        voidInvoice(g, open, invoiceVersion(g, open)).andExpect(status().isOk());
        assertThat(paymentStatus(g, reference)).isEqualTo("CANCELLED");
    }

    @Test
    void should_void_only_an_issued_invoice_without_a_credit_note() throws Exception {
        Shop g = garage("Anular Tres");
        String id = issuedInvoice(g);
        String noteId = idOf(credit(g, id, newRequestId()).andExpect(status().isCreated()));

        voidInvoice(g, id, invoiceVersion(g, id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-voidable"));
        voidInvoice(g, noteId, invoiceVersion(g, noteId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-voidable"));

        scenario.failInvoicing(Fault.UNAVAILABLE);
        String pending = idOf(issue(g.owner(), delivered(g), newRequestId()).andExpect(status().isAccepted()));
        voidInvoice(g, pending, invoiceVersion(g, pending)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-voidable"));
        call(post("/api/v1/invoices/" + pending + "/retry"), g.owner()).andExpect(status().isOk());
    }

    @Test
    void should_leave_the_invoice_alone_when_the_provider_cannot_void_it() throws Exception {
        Shop g = garage("Anular Quatro");
        String id = issuedInvoice(g);

        scenario.failVoids(Fault.UNAVAILABLE);
        voidInvoice(g, id, invoiceVersion(g, id)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("provider-unavailable"));
        scenario.failVoids(Fault.REJECT);
        voidInvoice(g, id, invoiceVersion(g, id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("provider-rejected"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"));

        voidInvoice(g, id, invoiceVersion(g, id)).andExpect(status().isOk());
    }

    @Test
    void should_issue_a_full_credit_note_that_mirrors_the_invoice() throws Exception {
        Shop g = garage("Credito Um");
        String id = issuedInvoice(g);
        String requestId = newRequestId();

        String note = credit(g, id, requestId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CREDIT_NOTE")).andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.number").value("NC SANDBOX/1")).andExpect(jsonPath("$.creditOfId").value(id))
                .andExpect(jsonPath("$.creditOfNumber").value("FT SANDBOX/1"))
                .andExpect(jsonPath("$.reason").value("Serviço cancelado"))
                .andExpect(jsonPath("$.totalCents").value(TOTAL)).andExpect(jsonPath("$.netCents").value(6250))
                .andExpect(jsonPath("$.lines.length()").value(2)).andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.paymentStatus").doesNotExist()).andExpect(jsonPath("$.balanceCents").value(0))
                .andReturn().getResponse().getContentAsString();

        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.paymentStatus").value("CREDITED")).andExpect(jsonPath("$.balanceCents").value(0))
                .andExpect(jsonPath("$.creditNote.id").value(idIn(note)))
                .andExpect(jsonPath("$.creditNote.number").value("NC SANDBOX/1"))
                .andExpect(jsonPath("$.creditNote.status").value("ISSUED"));
        // Replay and a second note.
        credit(g, id, requestId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(idIn(note)));
        credit(g, id, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("credit-note-exists"));
        // Not a credit note of a credit note, and no payment on the credited invoice.
        credit(g, idIn(note), newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-issued"));
        pay(g, id, "{\"method\":\"CASH\",\"amountCents\":100}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-issued"));
        pay(g, idIn(note), "{\"method\":\"CASH\",\"amountCents\":100}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-issued"));
    }

    @Test
    void should_credit_a_paid_invoice_and_report_what_is_owed_back() throws Exception {
        Shop g = garage("Credito Dois");
        String id = issuedInvoice(g);
        cash(g, id, 3000);
        String pendingReference = multibanco(g, id, 2000);

        credit(g, id, newRequestId()).andExpect(status().isCreated());

        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("CREDITED"))
                .andExpect(jsonPath("$.paidCents").value(3000)).andExpect(jsonPath("$.refundDueCents").value(3000))
                .andExpect(jsonPath("$.pendingCents").value(0)).andExpect(jsonPath("$.balanceCents").value(0));
        assertThat(paymentStatus(g, pendingReference)).isEqualTo("CANCELLED");
    }

    @Test
    void should_credit_a_pending_or_failed_note_again_after_dealing_with_it() throws Exception {
        Shop g = garage("Credito Tres");
        String id = issuedInvoice(g);
        scenario.failInvoicing(Fault.REJECT);
        String requestId = newRequestId();
        String note = idOf(credit(g, id, requestId).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.kind").value("CREDIT_NOTE")).andExpect(jsonPath("$.status").value("FAILED")));

        credit(g, id, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("credit-note-exists"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.creditNote.status").value("FAILED"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        call(post("/api/v1/invoices/" + note + "/retry"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED")).andExpect(jsonPath("$.number").value("NC SANDBOX/1"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("CREDITED"));
    }

    @Test
    void should_free_the_invoice_for_a_new_credit_note_after_a_failed_one_is_discarded() throws Exception {
        Shop g = garage("Credito Quatro");
        String id = issuedInvoice(g);
        scenario.failInvoicing(Fault.REJECT);
        String note = idOf(credit(g, id, newRequestId()).andExpect(status().isAccepted()));

        call(post("/api/v1/invoices/" + note + "/discard").content("{\"reason\":\"Motivo errado\",\"version\":"
                + invoiceVersion(g, note) + "}"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.creditNote").doesNotExist());
        credit(g, id, newRequestId()).andExpect(status().isCreated());
    }

    @Test
    void should_require_a_reason_for_a_credit_note() throws Exception {
        Shop g = garage("Credito Cinco");
        String id = issuedInvoice(g);

        call(post("/api/v1/invoices/" + id + "/credit-notes").content("{\"requestId\":\"" + newRequestId() + "\"}"),
                g.owner()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        call(post("/api/v1/invoices/" + id + "/credit-notes").content("{\"reason\":\"x\"}"), g.owner())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void should_require_the_version_to_void_or_discard() throws Exception {
        Shop g = garage("Anular Versao");
        String id = issuedInvoice(g);

        for (String action : new String[] {"void", "discard"}) {
            call(post("/api/v1/invoices/" + id + "/" + action).content("{\"reason\":\"Engano\"}"), g.owner())
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(jsonPath("$.errors.version").exists());
        }
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void should_not_let_a_discarded_credit_note_request_id_be_used_again() throws Exception {
        Shop g = garage("Credito Repetido");
        String id = issuedInvoice(g);
        String requestId = newRequestId();
        scenario.failInvoicing(Fault.REJECT);
        String note = idOf(credit(g, id, requestId).andExpect(status().isAccepted()));
        call(post("/api/v1/invoices/" + note + "/discard").content("{\"reason\":\"Errado\",\"version\":"
                + invoiceVersion(g, note) + "}"), g.owner()).andExpect(status().isOk());

        credit(g, id, requestId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request-id-reused"));
        credit(g, id, newRequestId()).andExpect(status().isCreated());
    }
}
