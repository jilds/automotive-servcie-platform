package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.reporting.application.ReportBulkhead;
import com.asp.platform.reporting.application.ReportService;

/// Reports are heavy reads: two at a time per workshop (the third is refused at once), at most
/// `app.reports.max-concurrent` (3) in all (the next waits `app.reports.max-wait`, 2 s); either way 429 `try-again`.
/// All reads of one report share one snapshot and the transaction has a timeout.
class ReportBulkheadTests extends ReportTestSupport {

    @Autowired
    ReportBulkhead bulkhead;

    private ResultActions get(Shop g) {
        try {
            return report(g, "kpis", PERIOD);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void should_let_a_workshop_run_two_reports_and_refuse_the_third_at_once_while_another_workshop_is_served()
            throws Exception {
        Shop busy = garage("Oficina Ocupada " + UUID.randomUUID());
        Shop other = garage("Oficina Livre " + UUID.randomUUID());
        UUID busyId = UUID.fromString(busy.id());

        // Two reports of this workshop are "running" (the permits are held by this thread): a third request is the
        // one refused, and it is refused at once.
        ResultActions[] answers = bulkhead.run(busyId, () -> bulkhead.run(busyId,
                () -> new ResultActions[] {get(busy), get(other)}));

        long start = System.nanoTime();
        answers[0].andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("try-again"));
        assertThat((System.nanoTime() - start) / 1_000_000).as("refused at once, no waiting").isLessThan(1000);
        assertThat(answers[0].andReturn().getResponse().getHeader("Retry-After")).isNotBlank();
        answers[1].andExpect(status().isOk());

        // One running report of the workshop leaves room for a second one: both are served.
        ResultActions second = bulkhead.run(busyId, () -> get(busy));
        second.andExpect(status().isOk());
        // Released: the workshop is served again.
        report(busy, "kpis", PERIOD).andExpect(status().isOk());
    }

    @Test
    void should_answer_429_try_again_with_retry_after_when_all_slots_are_taken() throws Exception {
        Shop g = garage("Oficina Comporta " + UUID.randomUUID());

        // Three reports of other workshops "running": the fourth finds no slot and waits out the 2 s.
        ResultActions busy = bulkhead.run(UUID.randomUUID(), () -> bulkhead.run(UUID.randomUUID(),
                () -> bulkhead.run(UUID.randomUUID(), () -> get(g))));
        busy.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("try-again"))
                .andExpect(jsonPath("$.type").value("/problems/try-again"));
        assertThat(busy.andReturn().getResponse().getHeader("Retry-After")).isNotBlank();

        report(g, "kpis", PERIOD).andExpect(status().isOk());
        report(g, "cost-vs-quote", PERIOD).andExpect(status().isOk());
        report(g, "productivity", PERIOD).andExpect(status().isOk());
    }

    @Test
    void should_read_every_statement_of_a_report_from_one_snapshot_and_bound_its_time() {
        Transactional tx = ReportService.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
        assertThat(tx.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        assertThat(tx.timeout()).isEqualTo(30);
    }
}
