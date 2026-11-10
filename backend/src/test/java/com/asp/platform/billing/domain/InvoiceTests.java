package com.asp.platform.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class InvoiceTests {

    @Test
    void should_clear_the_attempt_marker_when_the_document_is_discarded() {
        Invoice invoice = Fixtures.pending();
        invoice.markAttemptStarted(Fixtures.NOW);
        invoice.markRejected("sandbox-rejected", "no", Fixtures.NOW);
        assertThat(invoice.attemptStartedAt()).isNull();

        invoice.markAttemptStarted(Fixtures.NOW.plusSeconds(5));
        assertThat(invoice.attemptStartedAt()).isNotNull();
        invoice.discard("Engano", Fixtures.USER, Fixtures.NOW.plusSeconds(10));

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DISCARDED);
        assertThat(invoice.attemptStartedAt()).isNull();
    }

    @Test
    void should_know_when_an_attempt_is_in_flight() {
        Invoice invoice = Fixtures.pending();
        assertThat(invoice.attemptInFlight(Fixtures.NOW, Duration.ofSeconds(32))).isFalse();

        invoice.markAttemptStarted(Fixtures.NOW);

        assertThat(invoice.attemptInFlight(Fixtures.NOW.plusSeconds(31), Duration.ofSeconds(32))).isTrue();
        assertThat(invoice.attemptInFlight(Fixtures.NOW.plusSeconds(33), Duration.ofSeconds(32))).isFalse();
    }

    @Test
    void should_keep_the_issued_data_and_flag_a_document_issued_after_a_discard() {
        Invoice invoice = Fixtures.pending();
        invoice.markRejected("x", "no", Fixtures.NOW);
        invoice.discard("Cedo", Fixtures.USER, Fixtures.NOW);

        assertThat(invoice.markIssued(Fixtures.issuedDocument(1000), Fixtures.NOW.plusSeconds(1))).isTrue();

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DISCARDED);
        assertThat(invoice.issued()).isNotNull();
        assertThat(invoice.failure().code()).isEqualTo(Invoice.ISSUED_AFTER_DISCARD);
    }

    @Test
    void should_void_only_an_issued_invoice_and_discard_only_a_failed_one() {
        assertThatThrownBy(() -> Fixtures.pending().voidIt("x", Fixtures.USER, Fixtures.NOW))
                .isInstanceOf(InvoiceNotVoidableException.class);
        assertThatThrownBy(() -> Fixtures.issued().discard("x", Fixtures.USER, Fixtures.NOW))
                .isInstanceOf(InvoiceNotDiscardableException.class);
        assertThatThrownBy(() -> Fixtures.pending().discard("x", Fixtures.USER, Fixtures.NOW))
                .isInstanceOf(InvoiceNotDiscardableException.class);
    }

    @Test
    void should_count_an_attempt_when_it_starts_so_a_lost_result_still_counts() {
        Invoice invoice = Fixtures.pending();

        invoice.markAttemptStarted(Fixtures.NOW);
        assertThat(invoice.attempts()).isEqualTo(1);

        // The result does not count it a second time.
        invoice.markUnavailable("down", Fixtures.NOW.plusSeconds(1));
        assertThat(invoice.attempts()).isEqualTo(1);
        invoice.markAttemptStarted(Fixtures.NOW.plusSeconds(60));
        invoice.markRejected("x", "no", Fixtures.NOW.plusSeconds(61));
        assertThat(invoice.attempts()).isEqualTo(2);
        invoice.markAttemptStarted(Fixtures.NOW.plusSeconds(120));
        invoice.markIssued(Fixtures.issuedDocument(1000), Fixtures.NOW.plusSeconds(121));
        assertThat(invoice.attempts()).isEqualTo(3);
    }
}
