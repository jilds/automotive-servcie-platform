package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.asp.platform.billing.PaymentReceived;

import org.junit.jupiter.api.Test;

/// The provider webhook (ADR 0004 style): anonymous, the signature is checked before anything is read, the workshop
/// comes from the slug, every event is applied once.
@org.springframework.test.context.event.RecordApplicationEvents
class PaymentWebhookApiTests extends BillingTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.test.context.event.ApplicationEvents events;

    private final com.asp.platform.billing.infrastructure.SandboxScenario.Hold hold =
            new com.asp.platform.billing.infrastructure.SandboxScenario.Hold();

    private String event() {
        return "evt-" + UUID.randomUUID();
    }

    @Test
    void should_mark_a_payment_paid_when_the_provider_confirms_it() throws Exception {
        Shop g = garage("Webhook Um");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 0);

        signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "PAID", TOTAL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"));

        assertThat(paymentStatus(g, reference)).isEqualTo("PAID");
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.balanceCents").value(0));
        assertThat(count(g, "select count(*) from payment_event where outcome = 'APPLIED'")).isEqualTo(1);
    }

    @Test
    void should_add_partial_payments_up_to_the_total() throws Exception {
        Shop g = garage("Webhook Dois");
        String id = issuedInvoice(g);
        String first = multibanco(g, id, 2000);
        String second = mbway(g, id, "912345678");

        signedWebhook(g, webhookBody(event(), providerPaymentId(first), "PAID", 2000)).andExpect(status().isOk());
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.paidCents").value(2000)).andExpect(jsonPath("$.balanceCents").value(TOTAL - 2000));
        signedWebhook(g, webhookBody(event(), providerPaymentId(second), "PAID", TOTAL - 2000))
                .andExpect(jsonPath("$.result").value("APPLIED"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void should_refuse_a_missing_wrong_stale_or_tampered_signature_and_store_nothing() throws Exception {
        Shop g = garage("Webhook Tres");
        String reference = multibanco(g, issuedInvoice(g), 0);
        String body = webhookBody(event(), providerPaymentId(reference), "PAID", TOTAL);

        webhook(g, "sandbox", body, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("webhook-signature-invalid"));
        webhook(g, "sandbox", body, "t=" + Instant.now().getEpochSecond() + ",v1=" + "0".repeat(64))
                .andExpect(status().isUnauthorized());
        webhook(g, "sandbox", body, "garbage").andExpect(status().isUnauthorized());
        webhook(g, "sandbox", body, signature(body, Instant.now().getEpochSecond() - 600))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("webhook-signature-invalid"));
        webhook(g, "sandbox", body, signature(body, Instant.now().getEpochSecond() + 600))
                .andExpect(status().isUnauthorized());
        webhook(g, "sandbox", body.replace("PAID", "FAILED"), signature(body)).andExpect(status().isUnauthorized());

        assertThat(paymentStatus(g, reference)).isEqualTo("PENDING");
        assertThat(count(g, "select count(*) from payment_event")).isZero();
    }

    @Test
    void should_apply_an_event_once_and_answer_a_repeat_as_a_duplicate() throws Exception {
        Shop g = garage("Webhook Quatro");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 2000);
        String body = webhookBody(event(), providerPaymentId(reference), "PAID", 2000);

        signedWebhook(g, body).andExpect(jsonPath("$.result").value("APPLIED"));
        signedWebhook(g, body).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("DUPLICATE"));
        // Even a replay that is signed again with a new timestamp.
        webhook(g, "sandbox", body, signature(body, Instant.now().getEpochSecond() - 30))
                .andExpect(jsonPath("$.result").value("DUPLICATE"));

        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paidCents").value(2000));
        assertThat(count(g, "select count(*) from payment_event")).isEqualTo(1);
    }

    @Test
    void should_ignore_an_unknown_payment_and_an_amount_that_differs() throws Exception {
        Shop g = garage("Webhook Cinco");
        String reference = multibanco(g, issuedInvoice(g), 2000);

        signedWebhook(g, webhookBody(event(), "sbx-" + UUID.randomUUID(), "PAID", 2000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"));
        signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "PAID", 1999)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"));

        assertThat(paymentStatus(g, reference)).isEqualTo("PENDING");
        assertThat(count(g, "select count(*) from payment_event where outcome = 'IGNORED'")).isEqualTo(2);
    }

    @Test
    void should_never_apply_an_event_to_the_payment_of_another_workshop() throws Exception {
        Shop a = garage("Webhook Seis A");
        Shop b = garage("Webhook Seis B");
        String reference = multibanco(a, issuedInvoice(a), 2000);

        // B's URL, A's payment id: the workshop is the slug's.
        signedWebhook(b, webhookBody(event(), providerPaymentId(reference), "PAID", 2000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"));

        assertThat(paymentStatus(a, reference)).isEqualTo("PENDING");
        assertThat(count(a, "select count(*) from payment_event")).isZero();
        assertThat(count(b, "select count(*) from payment_event")).isEqualTo(1);
    }

    @Test
    void should_answer_404_for_another_provider_and_for_an_unknown_workshop() throws Exception {
        Shop g = garage("Webhook Sete");
        String body = webhookBody(event(), "sbx-x", "PAID", 1);

        webhook(g, "ifthenpay", body, signature(body)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("unknown-payment-provider"));
        publicCall(post("/api/v1/public/no-such-workshop-xyz/billing/webhooks/sandbox").content(body)
                .header("X-ASP-Signature", signature(body))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("workshop-not-found"));
    }

    @Test
    void should_fail_and_expire_a_pending_payment_and_take_late_money_after_a_cancel() throws Exception {
        Shop g = garage("Webhook Oito");
        String id = issuedInvoice(g);
        String mbway = mbway(g, id, "912345678");
        signedWebhook(g, webhookBody(event(), providerPaymentId(mbway), "FAILED", TOTAL))
                .andExpect(jsonPath("$.result").value("APPLIED"));
        assertThat(paymentStatus(g, mbway)).isEqualTo("FAILED");
        // A failed payment does not come back to life.
        signedWebhook(g, webhookBody(event(), providerPaymentId(mbway), "PAID", TOTAL))
                .andExpect(jsonPath("$.result").value("IGNORED"));

        String reference = multibanco(g, id, 0);
        signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "EXPIRED", TOTAL))
                .andExpect(jsonPath("$.result").value("APPLIED"));
        assertThat(paymentStatus(g, reference)).isEqualTo("EXPIRED");
        signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "PAID", TOTAL))
                .andExpect(jsonPath("$.result").value("APPLIED"));
        assertThat(paymentStatus(g, reference)).isEqualTo("PAID");

        String cancelled = multibanco(g, issuedInvoice(g), 0);
        call(post("/api/v1/payments/" + cancelled + "/cancel").content("{\"version\":" + paymentVersion(g, cancelled) + "}"),
                g.owner()).andExpect(status().isOk());
        signedWebhook(g, webhookBody(event(), providerPaymentId(cancelled), "PAID", TOTAL))
                .andExpect(jsonPath("$.result").value("APPLIED"));
        assertThat(paymentStatus(g, cancelled)).isEqualTo("PAID");
    }

    @Test
    void should_not_count_provider_calls_against_the_write_budget_of_the_public_forms() throws Exception {
        Shop g = garage("Webhook Nove");
        // The test configuration allows 3 public writes an hour; a provider may send many more from one address.
        for (int i = 0; i < 8; i++) {
            String body = webhookBody(event(), "sbx-" + UUID.randomUUID(), "PAID", 1);
            mvc.perform(post(webhookPath(g, "sandbox")).contentType("application/json").content(body)
                    .header("X-ASP-Signature", signature(body)).with(r -> {
                        r.setRemoteAddr("10.77.77.77");
                        return r;
                    })).andExpect(status().isOk());
        }
    }

    @Test
    void should_reject_an_unreadable_body_that_is_correctly_signed() throws Exception {
        Shop g = garage("Webhook Dez");
        String body = "not json";

        signedWebhook(g, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        String incomplete = "{\"eventId\":\"e1\"}";
        signedWebhook(g, incomplete).andExpect(status().isBadRequest());
        assertThat(count(g, "select count(*) from payment_event")).isZero();
    }

    @Test
    void should_refuse_a_body_over_16_kb_before_looking_at_the_signature_and_store_nothing() throws Exception {
        Shop g = garage("Webhook Grande");
        String reference = multibanco(g, issuedInvoice(g), 0);
        String body = "{\"eventId\":\"e-big\",\"paymentId\":\"" + providerPaymentId(reference)
                + "\",\"status\":\"PAID\",\"amountCents\":" + TOTAL + ",\"pad\":\"" + "a".repeat(17 * 1024) + "\"}";

        // Correctly signed, and still refused for its size; an unsigned one is refused for its size too, not 401.
        webhook(g, "sandbox", body, signature(body)).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("webhook-too-large"));
        webhook(g, "sandbox", body, null).andExpect(status().isContentTooLarge());

        assertThat(paymentStatus(g, reference)).isEqualTo("PENDING");
        assertThat(count(g, "select count(*) from payment_event")).isZero();
    }

    @Test
    void should_not_throttle_provider_calls_with_the_lookup_limit_of_the_public_pages() throws Exception {
        Shop a = garage("Webhook Limite A");
        Shop b = garage("Webhook Limite B");
        // The test configuration allows 40 public lookups a minute from one address; 100 webhook calls across two
        // workshops must all reach the signature check (401), none the limiter (429).
        for (int i = 0; i < 100; i++) {
            Shop g = i % 2 == 0 ? a : b;
            mvc.perform(post(webhookPath(g, "sandbox")).contentType("application/json").content("{}").with(r -> {
                r.setRemoteAddr("10.88.88.88");
                return r;
            })).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void should_surface_money_that_arrives_after_the_invoice_was_voided() throws Exception {
        Shop g = garage("Webhook Anulada");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 0);
        call(post("/api/v1/invoices/" + id + "/void").content("{\"reason\":\"Engano\",\"version\":"
                + invoiceVersion(g, id) + "}"), g.owner()).andExpect(status().isOk());
        assertThat(paymentStatus(g, reference)).isEqualTo("CANCELLED");

        signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "PAID", TOTAL))
                .andExpect(jsonPath("$.result").value("APPLIED"));

        assertThat(paymentStatus(g, reference)).isEqualTo("PAID");
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.paidCents").value(TOTAL)).andExpect(jsonPath("$.refundDueCents").value(TOTAL))
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());
        call(get("/api/v1/invoices?status=VOIDED"), g.owner())
                .andExpect(jsonPath("$.items[0].refundDueCents").value(TOTAL));
        assertThat(events.stream(PaymentReceived.class).filter(e -> e.paymentId().toString().equals(reference)))
                .singleElement().satisfies(e -> assertThat(e.lateOnClosedInvoice()).isTrue());
    }

    @Test
    void should_serialise_a_void_and_a_payment_that_arrives_while_it_runs() throws Exception {
        Shop g = garage("Webhook Corrida");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 0);
        long version = invoiceVersion(g, id);
        scenario.holdNextVoid(hold);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var voided = pool.submit(() -> call(post("/api/v1/invoices/" + id + "/void")
                    .content("{\"reason\":\"Engano\",\"version\":" + version + "}"), g.owner())
                    .andReturn().getResponse().getStatus());
            hold.entered().await();
            var webhook = pool.submit(() -> signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "PAID",
                    TOTAL)).andReturn().getResponse().getStatus());
            Thread.sleep(700);
            hold.release().countDown();

            assertThat(voided.get()).isEqualTo(200);
            assertThat(webhook.get()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
        // The void won the lock; the payment then arrived late and is kept as money to give back.
        assertThat(paymentStatus(g, reference)).isEqualTo("PAID");
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.refundDueCents").value(TOTAL));
    }

    @Test
    void should_report_the_overpaid_amount_when_a_cancelled_reference_is_paid_after_all() throws Exception {
        Shop g = garage("Webhook Excesso");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 0);
        call(post("/api/v1/payments/" + reference + "/cancel").content("{\"version\":" + paymentVersion(g, reference)
                + "}"), g.owner()).andExpect(status().isOk());
        cash(g, id, TOTAL);

        signedWebhook(g, webhookBody(event(), providerPaymentId(reference), "PAID", TOTAL))
                .andExpect(jsonPath("$.result").value("APPLIED"));

        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paidCents").value(2 * TOTAL))
                .andExpect(jsonPath("$.overpaidCents").value(TOTAL)).andExpect(jsonPath("$.balanceCents").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }
}
