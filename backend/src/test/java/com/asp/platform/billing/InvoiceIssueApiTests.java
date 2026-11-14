package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class InvoiceIssueApiTests extends BillingTestSupport {

    @Test
    void should_issue_the_invoice_of_a_delivered_work_order_from_its_current_lines() throws Exception {
        Shop g = garage("Faturas Um");
        String wo = delivered(g);

        String body = issue(g.owner(), wo, newRequestId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("INVOICE"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.workOrderId").value(wo))
                .andExpect(jsonPath("$.number").value("FT SANDBOX/1"))
                .andExpect(jsonPath("$.series").value("SANDBOX"))
                .andExpect(jsonPath("$.sequence").value(1))
                .andExpect(jsonPath("$.atcud").value("SANDBOX-1"))
                .andExpect(jsonPath("$.provider").value("sandbox"))
                .andExpect(jsonPath("$.sandbox").value(true))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.qrPayload").isNotEmpty())
                .andExpect(jsonPath("$.issuer.country").value("PT"))
                .andExpect(jsonPath("$.issuer.name").value("Faturas Um"))
                .andExpect(jsonPath("$.customer.name").value("Ana Pereira"))
                .andExpect(jsonPath("$.customer.nif").value("123456789"))
                .andExpect(jsonPath("$.customer.finalConsumer").value(false))
                .andExpect(jsonPath("$.customer.address").doesNotExist())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.netCents").value(6250))
                .andExpect(jsonPath("$.vatCents").value(1438))
                .andExpect(jsonPath("$.totalCents").value(TOTAL))
                .andExpect(jsonPath("$.vatSummary.length()").value(1))
                .andExpect(jsonPath("$.vatSummary[0].netCents").value(6250))
                .andExpect(jsonPath("$.vatSummary[0].vatCents").value(1438))
                .andExpect(jsonPath("$.paidCents").value(0))
                .andExpect(jsonPath("$.balanceCents").value(TOTAL))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.creditNote").doesNotExist())
                .andExpect(jsonPath("$.failure").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // The lines are the work order's own, amount by amount.
        String order = call(get("/api/v1/work-orders/" + wo), g.owner()).andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 2; i++) {
            assertThat((Number) JsonPath.read(body, "$.lines[" + i + "].netCents"))
                    .isEqualTo(JsonPath.read(order, "$.lines[" + i + "].netCents"));
            assertThat((String) JsonPath.read(body, "$.lines[" + i + "].description"))
                    .isEqualTo(JsonPath.read(order, "$.lines[" + i + "].description"));
        }
        assertThat((Number) JsonPath.read(body, "$.totalCents")).isEqualTo(JsonPath.read(order, "$.totalCents"));
        assertThat((Number) JsonPath.read(body, "$.lines[1].vatCents")).isEqualTo(518);

        call(get("/api/v1/work-orders/" + wo + "/invoices"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(idIn(body)));
    }

    @Test
    void should_number_each_workshop_on_its_own() throws Exception {
        Shop a = garage("Faturas Dois A");
        Shop b = garage("Faturas Dois B");

        issue(a.owner(), delivered(a), newRequestId()).andExpect(jsonPath("$.sequence").value(1));
        issue(a.owner(), delivered(a), newRequestId()).andExpect(jsonPath("$.number").value("FT SANDBOX/2"));
        issue(b.owner(), delivered(b), newRequestId()).andExpect(jsonPath("$.number").value("FT SANDBOX/1"));
    }

    @Test
    void should_refuse_an_order_that_is_not_delivered() throws Exception {
        Shop g = garage("Faturas Tres");
        String open = quotableOrder(g, false);
        issue(g.owner(), open, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-not-invoiceable"))
                .andExpect(jsonPath("$.status").value("DIAGNOSTICS"));

        String ready = quotableOrder(g, false);
        moveOk(g.owner(), ready, "IN_PROGRESS");
        moveOk(g.owner(), ready, "READY");
        issue(g.owner(), ready, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-not-invoiceable"))
                .andExpect(jsonPath("$.status").value("READY"));

        String cancelled = quotableOrder(g, false);
        moveOk(g.owner(), cancelled, "CANCELLED");
        issue(g.owner(), cancelled, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-not-invoiceable"));
        assertThat(count(g, "select count(*) from invoice")).isZero();
    }

    @Test
    void should_answer_404_for_an_unknown_work_order() throws Exception {
        Shop g = garage("Faturas Quatro");

        issue(g.owner(), java.util.UUID.randomUUID().toString(), newRequestId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
    }

    @Test
    void should_return_the_same_document_for_the_same_request_id_and_refuse_another_one() throws Exception {
        Shop g = garage("Faturas Cinco");
        String wo = delivered(g);
        String requestId = newRequestId();

        String id = idOf(issue(g.owner(), wo, requestId).andExpect(status().isCreated()));
        issue(g.owner(), wo, requestId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("ISSUED"));
        issue(g.owner(), wo, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already-invoiced"))
                .andExpect(jsonPath("$.invoiceId").value(id))
                .andExpect(jsonPath("$.invoiceStatus").value("ISSUED"));

        assertThat(count(g, "select count(*) from invoice")).isEqualTo(1);
        assertThat(count(g, "select count(*) from invoicing_sandbox_document")).isEqualTo(1);
    }

    @Test
    void should_issue_once_when_the_same_request_arrives_twice_at_the_same_time() throws Exception {
        Shop g = garage("Faturas Seis");
        String wo = delivered(g);
        String requestId = newRequestId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> call = () -> {
                var response = issue(g.owner(), wo, requestId).andReturn().getResponse();
                return response.getStatus() + ":" + JsonPath.read(response.getContentAsString(), "$.id");
            };
            Future<String> first = pool.submit(call);
            Future<String> second = pool.submit(call);
            List<String> results = List.of(first.get(), second.get());

            assertThat(results).allMatch(r -> r.startsWith("201:") || r.startsWith("200:"));
            assertThat(results.stream().map(r -> r.substring(4)).distinct()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(g, "select count(*) from invoice")).isEqualTo(1);
        assertThat(count(g, "select count(*) from invoicing_sandbox_document")).isEqualTo(1);
    }

    @Test
    void should_issue_to_a_final_consumer_without_nif_below_the_limit() throws Exception {
        Shop g = garage("Faturas Sete");
        String wo = delivered(g);

        issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"finalConsumer\":true}".formatted(newRequestId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Consumidor final"))
                .andExpect(jsonPath("$.customer.nif").doesNotExist())
                .andExpect(jsonPath("$.customer.finalConsumer").value(true))
                .andExpect(jsonPath("$.customer.address").doesNotExist());
    }

    @Test
    void should_use_the_customer_of_the_request_instead_of_the_vehicle_owner() throws Exception {
        Shop g = garage("Faturas Oito");
        String wo = delivered(g);

        issueWith(g.owner(), wo, """
                {"requestId":"%s","customer":{"name":"Frota Lda","nif":"509999999",
                 "address":{"street":"Rua Augusta, 120","detail":"2 Esq.","postalCode":"1100-053","locality":"Lisboa",
                            "country":"PT"}}}""".formatted(newRequestId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Frota Lda"))
                .andExpect(jsonPath("$.customer.nif").value("509999999"))
                .andExpect(jsonPath("$.customer.address.street").value("Rua Augusta, 120"))
                .andExpect(jsonPath("$.customer.address.postalCode").value("1100-053"))
                .andExpect(jsonPath("$.customer.address.country").value("PT"));
    }

    @Test
    void should_accept_a_customer_without_nif_below_the_limit() throws Exception {
        Shop g = garage("Faturas Nove");
        String wo = delivered(g);

        issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"customer\":{\"name\":\"Rui Costa\"}}"
                .formatted(newRequestId())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Rui Costa"))
                .andExpect(jsonPath("$.customer.nif").doesNotExist())
                .andExpect(jsonPath("$.customer.finalConsumer").value(false));
    }

    @Test
    void should_check_a_portuguese_nif() throws Exception {
        Shop g = garage("Faturas Dez");
        String wo = delivered(g);

        issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"customer\":{\"name\":\"Rui\",\"nif\":\"123456788\"}}"
                .formatted(newRequestId())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-nif"))
                .andExpect(jsonPath("$.errors['customer.nif']").exists());
        assertThat(count(g, "select count(*) from invoice")).isZero();
    }

    @Test
    void should_require_nif_and_address_above_the_final_consumer_limit() throws Exception {
        Shop g = garage("Faturas Onze");
        String wo = quotableOrder(g, false);
        labour(g, wo, "Motor novo", 200_000, 23);
        deliver(g, wo);

        // The owner has a NIF but no address.
        issue(g.owner(), wo, newRequestId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("customer-address-required"))
                .andExpect(jsonPath("$.errors['customer.address']").exists());
        issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"finalConsumer\":true}".formatted(newRequestId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("customer-nif-required"));
        issueWith(g.owner(), wo, """
                {"requestId":"%s","customer":{"name":"Rui","address":{"street":"Rua A","postalCode":"1100-053",
                 "locality":"Lisboa","country":"PT"}}}""".formatted(newRequestId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("customer-nif-required"))
                .andExpect(jsonPath("$.errors['customer.nif']").exists());
        assertThat(count(g, "select count(*) from invoice")).isZero();

        issueWith(g.owner(), wo, """
                {"requestId":"%s","customer":{"name":"Rui Costa","nif":"123456789",
                 "address":{"street":"Rua A, 1","postalCode":"1100-053","locality":"Lisboa","country":"PT"}}}"""
                .formatted(newRequestId())).andExpect(status().isCreated());
    }

    @Test
    void should_reject_a_request_without_request_id_or_with_a_bad_postal_code() throws Exception {
        Shop g = garage("Faturas Doze");
        String wo = delivered(g);

        issueWith(g.owner(), wo, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        issueWith(g.owner(), wo, """
                {"requestId":"%s","customer":{"name":"Rui","address":{"street":"Rua A","postalCode":"1100",
                 "locality":"Lisboa","country":"PT"}}}""".formatted(newRequestId())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void should_serve_a_pdf_marked_as_not_fiscal_for_an_issued_invoice() throws Exception {
        Shop g = garage("Faturas Treze");
        String id = issuedInvoice(g);

        var response = call(get("/api/v1/invoices/" + id + "/pdf"), g.owner()).andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("application/pdf");
        String pdf = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(pdf).startsWith("%PDF-").contains("SEM VALIDADE FISCAL").contains("FT SANDBOX/1")
                .contains("Ana Pereira");
        assertThat(pdf.trim()).endsWith("%%EOF");
    }

    @Test
    void should_describe_the_providers_in_the_billing_settings() throws Exception {
        Shop g = garage("Faturas Catorze");

        call(get("/api/v1/billing/settings"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicing.provider").value("sandbox"))
                .andExpect(jsonPath("$.invoicing.configured").value(true))
                .andExpect(jsonPath("$.invoicing.sandbox").value(true))
                .andExpect(jsonPath("$.payments.provider").value("sandbox"))
                .andExpect(jsonPath("$.payments.sandbox").value(true))
                .andExpect(jsonPath("$.finalConsumerLimitCents").value(100000))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.methods.length()").value(6))
                .andExpect(jsonPath("$.multibancoValidDays").value(3))
                .andExpect(jsonPath("$.mbwayValidMinutes").value(5));
    }

    @Test
    void should_let_the_front_desk_issue_too() throws Exception {
        Shop g = garage("Faturas Quinze");
        String wo = delivered(g);

        issue(member(g, "FRONT_DESK"), wo, newRequestId()).andExpect(status().isCreated());
    }

    @Test
    void should_refuse_a_customer_name_over_120_characters_with_its_own_code() throws Exception {
        Shop g = garage("Faturas Nome Longo");
        String wo = delivered(g);

        issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"customer\":{\"name\":\"%s\"}}".formatted(newRequestId(),
                "N".repeat(121))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("customer-name-too-long"))
                .andExpect(jsonPath("$.errors['customer.name']").exists());
        assertThat(count(g, "select count(*) from invoice")).isZero();
        issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"customer\":{\"name\":\"%s\"}}".formatted(newRequestId(),
                "N".repeat(120))).andExpect(status().isCreated());
    }

    @Test
    void should_refuse_a_customer_name_with_a_link_or_a_line_break() throws Exception {
        Shop g = garage("Faturas Nome Link");
        String wo = delivered(g);

        for (String name : new String[] {"http://evil.example", "www.evil.example", "Rui\\nCosta"}) {
            issueWith(g.owner(), wo, "{\"requestId\":\"%s\",\"customer\":{\"name\":\"%s\"}}".formatted(newRequestId(),
                    name)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(jsonPath("$.errors['customer.name']").exists());
        }
    }

    @Test
    void should_give_an_address_without_a_country_the_workshop_country() throws Exception {
        Shop g = garage("Faturas Pais");
        String wo = delivered(g);

        issueWith(g.owner(), wo, """
                {"requestId":"%s","customer":{"name":"Rui","address":{"street":"Rua A","postalCode":"1100",
                 "locality":"Lisboa"}}}""".formatted(newRequestId())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors['customer.address.postalCode']").exists());
        issueWith(g.owner(), wo, """
                {"requestId":"%s","customer":{"name":"Rui","address":{"street":"Rua A","postalCode":"1100-053",
                 "locality":"Lisboa"}}}""".formatted(newRequestId())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.address.country").value("PT"));
    }
}
