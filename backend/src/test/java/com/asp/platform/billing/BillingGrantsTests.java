package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.Test;

/// What the database lets the app role (`asp_app`) do with the billing tables (V20, V21): issued data never changes and
/// nothing is deleted. What the application does stays allowed.
class BillingGrantsTests extends BillingTestSupport {

    private int run(Shop g, String sql) {
        return asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                return s.executeUpdate();
            }
        });
    }

    @Test
    void should_refuse_to_the_app_role_what_the_application_never_does() throws Exception {
        Shop g = garage("Permissoes Faturas");
        String id = issuedInvoice(g);
        cash(g, id, 1000);
        String reference = multibanco(g, id, 0);
        signedWebhook(g, webhookBody("evt-grants", providerPaymentId(reference), "PAID", TOTAL - 1000));

        List<String> refused = List.of(
                "delete from invoice",
                "delete from invoice_line",
                "update invoice_line set net_cents = 1",
                "update invoice_line set description = 'x'",
                "update invoice set total_cents = 1",
                "update invoice set net_cents = 1",
                "update invoice set customer_name = 'x'",
                "update invoice set customer_nif = '999999990'",
                "update invoice set issuer_nif = '999999990'",
                "update invoice set work_order_id = work_order_id",
                "update invoice set kind = kind",
                "update invoice set request_id = request_id",
                "update invoice set created_by = created_by",
                "delete from payment",
                "update payment set amount_cents = 1",
                "update payment set method = method",
                "update payment set invoice_id = invoice_id",
                "update payment set mb_reference = 'x'",
                "delete from payment_event",
                "update payment_event set outcome = 'APPLIED'",
                "update payment_event set payment_id = null",
                "delete from invoicing_sandbox_document",
                "update invoicing_sandbox_document set number = 99",
                "update invoicing_sandbox_document set series = 'X'");
        for (String sql : refused) {
            assertThatThrownBy(() -> run(g, sql)).as(sql).rootCause().hasMessageContaining("permission denied");
        }

        List<String> allowed = List.of(
                "update invoice set status = status, version = version, updated_at = updated_at, attempt_started_at = null, "
                        + "failure_code = failure_code, failure_message = failure_message, attempts = attempts, "
                        + "last_attempt_at = last_attempt_at, closed_at = closed_at, closed_by = closed_by, "
                        + "closed_reason = closed_reason",
                "update payment set status = status, paid_at = paid_at, failure_code = failure_code, "
                        + "version = version, updated_at = updated_at",
                "update invoicing_sandbox_document set voided_at = voided_at");
        for (String sql : allowed) {
            assertThat(run(g, sql)).as(sql).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void should_refuse_to_change_the_issued_data_of_a_document_even_where_the_column_may_be_set_once() throws Exception {
        Shop g = garage("Imutavel Faturas");
        issuedInvoice(g);

        for (String column : List.of("atcud = 'X'", "series = 'OUTRA'", "sequence = 99", "full_number = 'FT X/9'",
                "provider_document_id = 'other'", "issued_at = now()", "qr_payload = 'x'", "hash_fragment = 'zzzz'")) {
            assertThatThrownBy(() -> run(g, "update invoice set " + column + " where status = 'ISSUED'"))
                    .as(column).rootCause().hasMessageContaining("immutable");
        }
    }

    @Test
    void should_keep_the_lifecycle_of_documents_and_payments_one_way() throws Exception {
        Shop g = garage("Ciclo Faturas");
        String issued = issuedInvoice(g);
        String paid = cash(g, issued, 1000);
        String pending = multibanco(g, issued, 2000);
        String voided = issuedInvoice(g);
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/invoices/" + voided
                + "/void").content("{\"reason\":\"Engano\",\"version\":" + invoiceVersion(g, voided) + "}"), g.owner())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // An issued document can only stay issued or be voided; a voided one is final; a closing never changes.
        for (String sql : List.of(
                "update invoice set status = 'PENDING' where id = '" + issued + "'",
                "update invoice set status = 'FAILED' where id = '" + issued + "'",
                "update invoice set status = 'ISSUED' where id = '" + voided + "'",
                "update invoice set status = 'DISCARDED' where id = '" + voided + "'",
                "update invoice set closed_reason = 'Outro' where id = '" + voided + "'",
                "update invoice set closed_at = now() where id = '" + voided + "'",
                // Paid payments are final; a paid one never changes its time.
                "update payment set status = 'PENDING', paid_at = null where id = '" + paid + "'",
                "update payment set status = 'CANCELLED', paid_at = null where id = '" + paid + "'",
                "update payment set paid_at = now() - interval '1 day' where id = '" + paid + "'")) {
            assertThatThrownBy(() -> run(g, sql)).as(sql).rootCause().hasMessageContaining("immutable");
        }
        // A pending payment may become anything; a cancelled one may still be paid (late money), nothing else.
        assertThat(run(g, "update payment set status = 'CANCELLED' where id = '" + pending + "'")).isEqualTo(1);
        assertThatThrownBy(() -> run(g, "update payment set status = 'PENDING' where id = '" + pending + "'"))
                .rootCause().hasMessageContaining("immutable");
        assertThat(run(g, "update payment set status = 'PAID', paid_at = now() where id = '" + pending + "'"))
                .isEqualTo(1);
    }

    @Test
    void should_refuse_a_line_added_to_a_document_that_is_no_longer_pending() throws Exception {
        Shop g = garage("Linhas Faturas");
        issuedInvoice(g);

        assertThatThrownBy(() -> run(g, """
                insert into invoice_line (id, invoice_id, position, kind, description, quantity, unit_price_cents,
                                          discount_percent, vat_rate, net_cents, vat_cents)
                select gen_random_uuid(), id, 99, 'LABOR', 'x', 1, 1, 0, 23, 1, 0 from invoice limit 1"""))
                .rootCause().hasMessageContaining("immutable");
    }

    @Test
    void should_refuse_to_discard_a_pending_document_even_at_the_database() throws Exception {
        Shop g = garage("Pendente Faturas");
        scenario.failInvoicing(com.asp.platform.billing.infrastructure.SandboxScenario.Fault.UNAVAILABLE);
        String pending = idOf(issue(g.owner(), delivered(g), newRequestId())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted()));

        assertThatThrownBy(() -> run(g, "update invoice set status = 'DISCARDED', closed_at = now(), closed_by = "
                + "created_by, closed_reason = 'x' where id = '" + pending + "'")).rootCause()
                .hasMessageContaining("immutable");
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/invoices/" + pending
                + "/retry"), g.owner())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    private static final String INSERT_DISCARDED = """
            insert into invoice (id, kind, status, work_order_id, work_order_number, request_id, issuer_name,
                                 issuer_country, customer_name, net_cents, vat_cents, total_cents, provider,
                                 provider_document_id, series, sequence, full_number, atcud, qr_payload, hash_fragment,
                                 issued_at, closed_at, closed_by, closed_reason, created_by, created_at, updated_at)
            select gen_random_uuid(), 'INVOICE', 'DISCARDED', work_order_id, 1, gen_random_uuid(), 'x', 'PT', 'x',
                   0, 0, 0, 'sandbox', %s, %s, %s, %s, %s, %s, %s, %s, now(), created_by, 'x', created_by, now(),
                   now()
            from invoice limit 1""";

    @Test
    void should_keep_the_provider_data_of_a_document_all_or_nothing_and_bounded() throws Exception {
        Shop g = garage("Dados Faturas");
        issuedInvoice(g);

        // Only the number set: the other provider columns must be set with it.
        assertThatThrownBy(() -> asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement(INSERT_DISCARDED.formatted("null", "null", "null",
                    "'FT X/1'", "null", "null", "null", "null"))) {
                return s.executeUpdate();
            }
        })).hasMessageContaining("invoice_issued_all_or_nothing");
        // All set, one too long.
        assertThatThrownBy(() -> asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement(INSERT_DISCARDED.formatted("'d'", "'s'", "1", "'FT X/1'",
                    "repeat('x', 101)", "'q'", "'h'", "now()"))) {
                return s.executeUpdate();
            }
        })).hasMessageContaining("invoice_provider_data_length");
    }

    @Test
    void should_only_let_provider_payments_carry_provider_data() throws Exception {
        Shop g = garage("Metodo Pagamentos");
        issuedInvoice(g);

        for (String column : List.of("mb_reference", "mb_entity", "mbway_phone_masked")) {
            String value = "mb_entity".equals(column) ? "'99999'" : "mb_reference".equals(column) ? "'123456789'"
                    : "'+351 9** *** 678'";
            String check = "mbway_phone_masked".equals(column) ? "payment_only_mbway_has_phone"
                    : "payment_only_multibanco_has_reference";
            assertThatThrownBy(() -> asOwner(g, c -> {
                try (PreparedStatement s = c.prepareStatement("insert into payment (id, invoice_id, method, status, "
                        + "amount_cents, " + column + ", paid_at, created_by, created_at, updated_at) select "
                        + "gen_random_uuid(), id, 'CASH', 'PAID', 1, " + value + ", now(), created_by, now(), now() "
                        + "from invoice limit 1")) {
                    return s.executeUpdate();
                }
            })).as(column).hasMessageContaining(check);
        }
    }
}
