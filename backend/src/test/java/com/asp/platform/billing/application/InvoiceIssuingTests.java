package com.asp.platform.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvoiceIssuingTests {

    @Test
    void should_cut_a_missing_message_to_nothing_and_a_long_one_to_the_limit() {
        assertThat(InvoiceIssuing.cut(null)).isEmpty();
        assertThat(InvoiceIssuing.cut("x".repeat(500))).hasSize(300);
        assertThat(InvoiceIssuing.cut("short")).isEqualTo("short");
    }

    @Test
    void should_default_a_missing_or_blank_failure_code_and_cut_a_long_one() {
        assertThat(InvoiceIssuing.failureCode(null)).isEqualTo("provider-error");
        assertThat(InvoiceIssuing.failureCode("  ")).isEqualTo("provider-error");
        assertThat(InvoiceIssuing.failureCode("sandbox-rejected")).isEqualTo("sandbox-rejected");
        assertThat(InvoiceIssuing.failureCode("c".repeat(100))).hasSize(60);
    }
}
