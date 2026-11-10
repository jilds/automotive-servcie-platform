package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.asp.platform.billing.application.InvoiceRetry;
import com.asp.platform.billing.infrastructure.SandboxScenario.Fault;

/// A request is never lost: the snapshot is stored before the provider is called, and a failed or unanswered call
/// can be retried, by hand or by the job, without issuing twice.
class InvoiceRetryApiTests extends BillingTestSupport {

    @Autowired
    InvoiceRetry retryJob;

    @Test
    void should_keep_the_document_pending_when_the_provider_is_unavailable_and_issue_it_on_retry() throws Exception {
        Shop g = garage("Retry Um");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.UNAVAILABLE);

        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.failure.code").value("provider-unavailable"))
                .andExpect(jsonPath("$.failure.attempts").value(1))
                .andExpect(jsonPath("$.totalCents").value(TOTAL)));

        call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED")).andExpect(jsonPath("$.sequence").value(1))
                .andExpect(jsonPath("$.failure").doesNotExist());
    }

    @Test
    void should_not_issue_twice_when_the_answer_was_lost_after_the_provider_issued() throws Exception {
        Shop g = garage("Retry Dois");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.UNAVAILABLE_AFTER_ISSUE);

        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")));

        call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED")).andExpect(jsonPath("$.number").value("FT SANDBOX/1"));
        assertThat(count(g, "select count(*) from invoicing_sandbox_document")).isEqualTo(1);
        // The next invoice takes number 2: no number was burned.
        issue(g.owner(), delivered(g), newRequestId()).andExpect(jsonPath("$.number").value("FT SANDBOX/2"));
    }

    @Test
    void should_complete_a_pending_document_when_the_same_request_comes_again() throws Exception {
        Shop g = garage("Retry Tres");
        String wo = delivered(g);
        String requestId = newRequestId();
        scenario.failInvoicing(Fault.UNAVAILABLE);
        String id = idOf(issue(g.owner(), wo, requestId).andExpect(status().isAccepted()));

        issue(g.owner(), wo, requestId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("ISSUED"));
        assertThat(count(g, "select count(*) from invoice")).isEqualTo(1);
    }

    @Test
    void should_store_a_rejected_document_as_failed_and_block_a_second_invoice_until_it_is_dealt_with() throws Exception {
        Shop g = garage("Retry Quatro");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.REJECT);

        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure.code").value("sandbox-rejected"))
                .andExpect(jsonPath("$.failure.message").isNotEmpty()));

        issue(g.owner(), wo, newRequestId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already-invoiced"))
                .andExpect(jsonPath("$.invoiceStatus").value("FAILED"));
        call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void should_discard_a_failed_document_and_free_the_work_order() throws Exception {
        Shop g = garage("Retry Cinco");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.REJECT);
        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted()));
        long version = invoiceVersion(g, id);

        call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\"NIF errado\",\"version\":" + (version + 1)
                + "}"), g.owner()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale-data"));
        call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\" \",\"version\":" + version + "}"),
                g.owner()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\"NIF errado\",\"version\":" + version
                + "}"), g.owner()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.reason").value("NIF errado")).andExpect(jsonPath("$.closedAt").isNotEmpty());

        String second = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED")));
        assertThat(second).isNotEqualTo(id);
        call(get("/api/v1/work-orders/" + wo + "/invoices"), g.owner()).andExpect(jsonPath("$.length()").value(2));
        call(get("/api/v1/invoices/" + id + "/pdf"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-issued"));
        call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-retryable"));
    }

    @Test
    void should_only_discard_failed_and_only_retry_pending_or_failed() throws Exception {
        Shop g = garage("Retry Seis");
        String issued = issuedInvoice(g);
        call(post("/api/v1/invoices/" + issued + "/retry"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-retryable"));
        call(post("/api/v1/invoices/" + issued + "/discard").content("{\"reason\":\"x\",\"version\":"
                + invoiceVersion(g, issued) + "}"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-discardable"));

        // A pending document may have been issued by the provider: it is never discarded.
        scenario.failInvoicing(Fault.UNAVAILABLE);
        String pending = idOf(issue(g.owner(), delivered(g), newRequestId()).andExpect(status().isAccepted()));
        call(post("/api/v1/invoices/" + pending + "/discard").content("{\"reason\":\"x\",\"version\":"
                + invoiceVersion(g, pending) + "}"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-discardable"));
        call(get("/api/v1/invoices/" + pending + "/pdf"), g.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invoice-not-issued"));
        // Leave nothing pending behind: the retry job works on every workshop and would use the next scripted fault.
        call(post("/api/v1/invoices/" + pending + "/retry"), g.owner()).andExpect(status().isOk());
    }

    @Test
    void should_retry_pending_documents_in_the_job_until_the_attempts_run_out() throws Exception {
        Shop g = garage("Retry Sete");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.UNAVAILABLE, Fault.UNAVAILABLE, Fault.UNAVAILABLE, Fault.UNAVAILABLE,
                Fault.UNAVAILABLE);
        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted()));
        Instant later = Instant.now().plus(1, ChronoUnit.HOURS);

        // Attempts 2 to 5 fail, then the job leaves it to staff.
        for (int attempt = 2; attempt <= 5; attempt++) {
            assertThat(retryJob.retryDue(later.plus(attempt, ChronoUnit.HOURS))).as("run %d", attempt).isGreaterThanOrEqualTo(1);
        }
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.failure.attempts").value(5));
        retryJob.retryDue(later.plus(20, ChronoUnit.HOURS));
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.failure.attempts").value(5));

        // Staff can still retry by hand once the provider is back.
        call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void should_issue_a_pending_document_in_the_job_when_the_provider_is_back() throws Exception {
        Shop g = garage("Retry Oito");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.UNAVAILABLE);
        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted()));

        // Too soon after the last attempt: left alone.
        retryJob.retryDue(Instant.now());
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(retryJob.retryDue(Instant.now().plus(1, ChronoUnit.HOURS))).isGreaterThanOrEqualTo(1);

        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void should_refuse_to_discard_while_an_attempt_is_in_flight_and_flag_a_document_issued_after_a_discard()
            throws Exception {
        Shop g = garage("Retry Corrida");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.REJECT);
        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted()));
        var hold = new com.asp.platform.billing.infrastructure.SandboxScenario.Hold();
        scenario.holdNextInvoicing(hold);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var retry = pool.submit(() -> call(post("/api/v1/invoices/" + id + "/retry"), g.owner())
                    .andReturn().getResponse().getStatus());
            hold.entered().await();

            // The provider may be issuing it right now: discarding would free the order for a second invoice.
            call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\"Cedo\",\"version\":"
                    + invoiceVersion(g, id) + "}"), g.owner()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("try-again"));

            // An attempt that never came back (older than the provider timeout plus 30 s) no longer blocks it.
            asOwner(g, c -> {
                try (var s = c.prepareStatement("update invoice set attempt_started_at = now() - interval '5 minutes' "
                        + "where id = ?::uuid")) {
                    s.setString(1, id);
                    return s.executeUpdate();
                }
            });
            call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\"Presa\",\"version\":"
                    + invoiceVersion(g, id) + "}"), g.owner()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISCARDED"));

            hold.release().countDown();
            assertThat(retry.get()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        // The provider did issue it: the number is kept and the document is flagged, not hidden.
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.number").value("FT SANDBOX/1"))
                .andExpect(jsonPath("$.failure.code").value("issued-after-discard"));
    }

    @Test
    void should_not_let_a_discarded_request_id_issue_again() throws Exception {
        Shop g = garage("Retry Repetido");
        String wo = delivered(g);
        String requestId = newRequestId();
        scenario.failInvoicing(Fault.REJECT);
        String id = idOf(issue(g.owner(), wo, requestId).andExpect(status().isAccepted()));
        call(post("/api/v1/invoices/" + id + "/discard").content("{\"reason\":\"Errado\",\"version\":"
                + invoiceVersion(g, id) + "}"), g.owner()).andExpect(status().isOk());

        issue(g.owner(), wo, requestId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request-id-reused"));
        issue(g.owner(), wo, newRequestId()).andExpect(status().isCreated());
    }

    @Test
    void should_fail_visibly_when_the_provider_issues_another_total() throws Exception {
        Shop g = garage("Retry Total");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.WRONG_TOTAL);

        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure.code").value("provider-total-mismatch"))
                .andExpect(jsonPath("$.failure.message").value(org.hamcrest.Matchers.containsString("FT SANDBOX/1"))));

        // The sandbox ledger answers with the right total the second time.
        call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void should_answer_try_again_to_a_second_retry_while_the_first_is_with_the_provider() throws Exception {
        Shop g = garage("Retry Duplo");
        String wo = delivered(g);
        scenario.failInvoicing(Fault.REJECT);
        String id = idOf(issue(g.owner(), wo, newRequestId()).andExpect(status().isAccepted()));
        var hold = new com.asp.platform.billing.infrastructure.SandboxScenario.Hold();
        scenario.holdNextInvoicing(hold);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> call(post("/api/v1/invoices/" + id + "/retry"), g.owner())
                    .andReturn().getResponse().getStatus());
            hold.entered().await();

            call(post("/api/v1/invoices/" + id + "/retry"), g.owner()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("try-again"));

            hold.release().countDown();
            assertThat(first.get()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
        call(get("/api/v1/invoices/" + id), g.owner()).andExpect(jsonPath("$.status").value("ISSUED"));
        assertThat(count(g, "select count(*) from invoicing_sandbox_document")).isEqualTo(1);
    }
}
