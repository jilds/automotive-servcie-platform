package com.asp.platform.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/// The CSV Excel in Portugal opens: BOM, `;`, decimal comma, CRLF, quoting, and no formula from user text.
class ReportCsvTests {

    @Test
    void should_write_a_bom_a_header_and_crlf_lines() {
        String csv = Csv.document(List.of("a", "b"), List.of(List.of("1", "2"), List.of("3", "4")));

        assertThat(csv).isEqualTo("﻿a;b\r\n1;2\r\n3;4\r\n");
    }

    @Test
    void should_write_only_the_header_without_rows() {
        assertThat(Csv.document(List.of("a", "b"), List.of())).isEqualTo("﻿a;b\r\n");
    }

    @Test
    void should_write_money_in_euros_with_a_decimal_comma_and_no_thousands_separator() {
        assertThat(Csv.euros(12345L)).isEqualTo("123,45");
        assertThat(Csv.euros(5L)).isEqualTo("0,05");
        assertThat(Csv.euros(0L)).isEqualTo("0,00");
        assertThat(Csv.euros(123456789L)).isEqualTo("1234567,89");
        assertThat(Csv.euros(-5600L)).isEqualTo("-56,00");
        assertThat(Csv.euros(-5L)).isEqualTo("-0,05");
        assertThat(Csv.euros(null)).isEmpty();
    }

    @Test
    void should_write_decimals_with_two_places_and_a_comma() {
        assertThat(Csv.decimal(new BigDecimal("2.5"))).isEqualTo("2,50");
        assertThat(Csv.decimal(new BigDecimal("0.43"))).isEqualTo("0,43");
        assertThat(Csv.decimal(new BigDecimal("-86.15"))).isEqualTo("-86,15");
        assertThat(Csv.decimal(null)).isEmpty();
    }

    @Test
    void should_leave_plain_text_alone() {
        assertThat(Csv.text("Oficina Lda")).isEqualTo("Oficina Lda");
        assertThat(Csv.text(null)).isEmpty();
        assertThat(Csv.text("")).isEmpty();
    }

    @Test
    void should_quote_text_with_the_separator_quotes_or_line_breaks() {
        assertThat(Csv.text("a;b")).isEqualTo("\"a;b\"");
        assertThat(Csv.text("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(Csv.text("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(Csv.text("two\r\nlines")).isEqualTo("\"two\r\nlines\"");
    }

    @Test
    void should_neutralise_text_that_a_spreadsheet_would_run_as_a_formula() {
        assertThat(Csv.text("=1+1")).isEqualTo("'=1+1");
        assertThat(Csv.text("+351912345678")).isEqualTo("'+351912345678");
        assertThat(Csv.text("-2+3")).isEqualTo("'-2+3");
        assertThat(Csv.text("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.text("\tcmd")).isEqualTo("'\tcmd");
        assertThat(Csv.text("\rcmd")).isEqualTo("\"'\rcmd\"");
    }

    @Test
    void should_neutralise_first_and_then_quote_when_both_apply() {
        assertThat(Csv.text("=HYPERLINK(\"x\";\"y\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\";\"\"y\"\")\"");
    }

    @Test
    void should_only_look_at_the_first_character() {
        assertThat(Csv.text("a=b")).isEqualTo("a=b");
        assertThat(Csv.text("AA-00-AA")).isEqualTo("AA-00-AA");
    }
}
