package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.billing.application.PaymentExpiry;
import com.asp.platform.billing.infrastructure.SandboxScenario.Fault;
import com.jayway.jsonpath.JsonPath;

class PaymentApiTests extends BillingTestSupport {

    @Autowired
    PaymentExpiry expiry;

    private ResultActions simulate(Shop g, String paymentId, String outcome) throws Exception {
        return call(post("/api/v1/payments/" + paymentId + "/sandbox/simulate")
                .content("{\"outcome\":\"" + outcome + "\"}"), g.owner());
    }

    @Test
    void should_record_partial_payments_by_staff_and_track_the_balance() throws Exception {
        Shop g = garage("Pagar Um");
        String id = issuedInvoice(g);

        pay(g, id, "{\"method\":\"CASH\",\"amountCents\":3000,\"note\":\"Troco dado\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.method").value("CASH")).andExpect(jsonPath("$.amountCents").value(3000))
                .andExpect(jsonPath("$.paidAt").isNotEmpty()).andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.multibanco").doesNotExist()).andExpect(jsonPath("$.mbway").doesNotExist())
                .andExpect(jsonPath("$.note").value("Troco dado")).andExpect(jsonPath("$.invoiceId").value(id));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.paidCents").value(3000)).andExpect(jsonPath("$.balanceCents").value(TOTAL - 3000))
                .andExpect(jsonPath("$.payments.length()").value(1));

        // No amount: what is left.
        pay(g, id, "{\"method\":\"CARD\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(TOTAL - 3000));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.balanceCents").value(0)).andExpect(jsonPath("$.payments.length()").value(2));
        pay(g, id, "{\"method\":\"TRANSFER\",\"amountCents\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment-exceeds-balance")).andExpect(jsonPath("$.leftCents").value(0));
    }

    @Test
    void should_refuse_an_amount_above_the_balance_or_not_positive() throws Exception {
        Shop g = garage("Pagar Dois");
        String id = issuedInvoice(g);

        pay(g, id, "{\"method\":\"CASH\",\"amountCents\":" + (TOTAL + 1) + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment-exceeds-balance"))
                .andExpect(jsonPath("$.leftCents").value(TOTAL));
        pay(g, id, "{\"method\":\"CASH\",\"amountCents\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        pay(g, id, "{\"amountCents\":10}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        pay(g, id, "{\"method\":\"BITCOIN\",\"amountCents\":10}").andExpect(status().isBadRequest());
        assertThat(count(g, "select count(*) from payment")).isZero();
    }

    @Test
    void should_create_a_multibanco_reference_that_counts_against_the_balance_and_is_paid_by_the_provider()
            throws Exception {
        Shop g = garage("Pagar Tres");
        String id = issuedInvoice(g);

        String body = pay(g, id, "{\"method\":\"MULTIBANCO\",\"amountCents\":5000}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.method").value("MULTIBANCO"))
                .andExpect(jsonPath("$.provider").value("sandbox")).andExpect(jsonPath("$.multibanco.entity").value("99999"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty()).andExpect(jsonPath("$.paidAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(body, "$.multibanco.reference");
        assertThat(reference).matches("\\d{9}");
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofDays(3).minusMinutes(5),
                Duration.ofDays(3).plusMinutes(5));

        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.pendingCents").value(5000))
                .andExpect(jsonPath("$.paidCents").value(0)).andExpect(jsonPath("$.balanceCents").value(TOTAL))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        pay(g, id, "{\"method\":\"CASH\",\"amountCents\":" + (TOTAL - 4999) + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment-exceeds-balance"))
                .andExpect(jsonPath("$.leftCents").value(TOTAL - 5000));

        String paymentId = JsonPath.read(body, "$.id");
        simulate(g, paymentId, "PAID").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paidCents").value(5000))
                .andExpect(jsonPath("$.pendingCents").value(0)).andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        simulate(g, paymentId, "PAID").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment-not-pending"));
        pay(g, id, "{\"method\":\"MULTIBANCO\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(TOTAL - 5000));
    }

    @Test
    void should_run_the_mb_way_lifecycle() throws Exception {
        Shop g = garage("Pagar Quatro");
        String id = issuedInvoice(g);

        pay(g, id, "{\"method\":\"MBWAY\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-phone")).andExpect(jsonPath("$.errors.phone").exists());
        pay(g, id, "{\"method\":\"MBWAY\",\"phone\":\"212345678\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-phone")).andExpect(jsonPath("$.errors.phone").exists());

        String body = pay(g, id, "{\"method\":\"MBWAY\",\"phone\":\"+351 912 345 678\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.mbway.phoneMasked").value("+351 9** *** 678"))
                .andExpect(jsonPath("$.multibanco").doesNotExist()).andExpect(jsonPath("$.amountCents").value(TOTAL))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofMinutes(4), Duration.ofMinutes(6));
        // The phone number itself is not kept.
        assertThat(count(g, "select count(*) from payment where mbway_phone_masked like '%912345678%'")).isZero();

        call(post("/api/v1/payments/" + paymentId + "/refresh"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        simulate(g, paymentId, "FAILED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").isNotEmpty());
        call(post("/api/v1/payments/" + paymentId + "/refresh"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment-not-pending"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.balanceCents").value(TOTAL))
                .andExpect(jsonPath("$.pendingCents").value(0));

        String second = mbway(g, id, "912345678");
        simulate(g, second, "PAID").andExpect(jsonPath("$.status").value("PAID"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.balanceCents").value(0));
    }

    @Test
    void should_store_nothing_when_the_payment_provider_refuses_or_is_down() throws Exception {
        Shop g = garage("Pagar Cinco");
        String id = issuedInvoice(g);

        scenario.failPayments(Fault.REJECT, Fault.UNAVAILABLE);
        pay(g, id, "{\"method\":\"MBWAY\",\"phone\":\"912345678\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment-failed")).andExpect(jsonPath("$.reason").isNotEmpty());
        pay(g, id, "{\"method\":\"MULTIBANCO\"}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("provider-unavailable"));

        assertThat(count(g, "select count(*) from payment")).isZero();
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.pendingCents").value(0));
        multibanco(g, id, 0);
    }

    @Test
    void should_cancel_a_pending_payment_but_not_a_paid_one() throws Exception {
        Shop g = garage("Pagar Seis");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 2000);
        long version = paymentVersion(g, reference);

        call(post("/api/v1/payments/" + reference + "/cancel").content("{\"version\":" + (version + 1) + "}"), g.owner())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale-data"));
        call(post("/api/v1/payments/" + reference + "/cancel").content("{\"version\":" + version + "}"), g.owner())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.pendingCents").value(0));

        String cash = cash(g, id, 1000);
        call(post("/api/v1/payments/" + cash + "/cancel").content("{\"version\":" + paymentVersion(g, cash) + "}"),
                g.owner()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("payment-not-pending"));
    }

    @Test
    void should_not_take_payment_on_a_document_that_is_not_issued() throws Exception {
        Shop g = garage("Pagar Sete");
        scenario.failInvoicing(Fault.UNAVAILABLE);
        String pending = idOf(issue(g.owner(), delivered(g), newRequestId()).andExpect(status().isAccepted()));

        pay(g, pending, "{\"method\":\"CASH\",\"amountCents\":100}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-issued"));
        call(post("/api/v1/invoices/" + pending + "/retry"), g.owner()).andExpect(status().isOk());
        pay(g, pending, "{\"method\":\"CASH\",\"amountCents\":100}").andExpect(status().isCreated());
    }

    @Test
    void should_expire_pending_references_in_the_job_and_still_accept_late_money() throws Exception {
        Shop g = garage("Pagar Oito");
        String id = issuedInvoice(g);
        String reference = multibanco(g, id, 0);
        asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "update payment set expires_at = now() - interval '1 hour' where id = ?::uuid")) {
                s.setString(1, reference);
                return s.executeUpdate();
            }
        });

        assertThat(expiry.expireDue(Instant.now())).isGreaterThanOrEqualTo(1);
        assertThat(paymentStatus(g, reference)).isEqualTo("EXPIRED");
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.pendingCents").value(0))
                .andExpect(jsonPath("$.paidCents").value(0));

        // The money came after all.
        simulate(g, reference, "PAID").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void should_record_payments_without_a_payment_provider_for_staff_methods_only() throws Exception {
        // The payment provider is the sandbox here; the unconfigured case is in ProviderNotConfiguredApiTests.
        Shop g = garage("Pagar Nove");
        String id = issuedInvoice(g);

        pay(g, id, "{\"method\":\"TRANSFER\",\"amountCents\":100,\"note\":\"IBAN 1234\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.method").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("PAID"));
        call(get("/api/v1/payments/" + java.util.UUID.randomUUID()), g.owner()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment-not-found"));
    }

    @Test
    void should_record_an_instalment_sale_approved_on_the_terminal_as_paid_at_once() throws Exception {
        Shop g = garage("Pagar Parcela");
        String id = issuedInvoice(g);

        pay(g, id, "{\"method\":\"INSTALMENTS\",\"amountCents\":5000,"
                + "\"instalment\":{\"financier\":\"PARCELA_JA\",\"count\":4,\"reference\":\" 000123 \"}}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.method").value("INSTALMENTS")).andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.instalment.financier").value("PARCELA_JA"))
                .andExpect(jsonPath("$.instalment.count").value(4))
                .andExpect(jsonPath("$.instalment.reference").value("000123"));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.paidCents").value(5000))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        call(get("/api/v1/billing/settings"), g.owner()).andExpect(jsonPath("$.methods").value(
                org.hamcrest.Matchers.hasItem("INSTALMENTS")));
    }

    @Test
    void should_refuse_instalment_data_that_is_missing_misplaced_or_out_of_range() throws Exception {
        Shop g = garage("Pagar Parcela Erro");
        String id = issuedInvoice(g);

        pay(g, id, "{\"method\":\"INSTALMENTS\",\"amountCents\":100}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("instalment-required"));
        pay(g, id, "{\"method\":\"CARD\",\"amountCents\":100,\"instalment\":{\"financier\":\"OTHER\",\"count\":3}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("instalment-required"));
        pay(g, id, "{\"method\":\"INSTALMENTS\",\"amountCents\":100,\"instalment\":{\"financier\":\"OTHER\",\"count\":1}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-instalment-count"));
        pay(g, id, "{\"method\":\"INSTALMENTS\",\"amountCents\":100,\"instalment\":{\"financier\":\"OTHER\",\"count\":7}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-instalment-count"));
        pay(g, id, "{\"method\":\"INSTALMENTS\",\"amountCents\":100,\"instalment\":{\"count\":3}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        assertThat(count(g, "select count(*) from payment")).isZero();
    }

    @Test
    void should_require_the_version_to_cancel_a_payment() throws Exception {
        Shop g = garage("Pagar Versao");
        String reference = multibanco(g, issuedInvoice(g), 0);

        call(post("/api/v1/payments/" + reference + "/cancel").content("{}"), g.owner())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.version").exists());
        assertThat(paymentStatus(g, reference)).isEqualTo("PENDING");
    }
}
