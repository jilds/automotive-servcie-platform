package com.asp.platform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Names and the district go into a mail and its subject: one line, no links.
class LeadRequestPatternTests {

    @ParameterizedTest
    @ValueSource(strings = {"Oficina Azul", "Auto São João, Lda.", "Lisboa", "A&B (Norte) - 2", ""})
    void should_accept_plain_one_line_text(String value) {
        assertThat(value.matches(LeadRequest.ONE_LINE_NO_LINK)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Visite https://x.example", "WWW.x.example", "a<b", "a>b", "a\nb", "a\tb", "a\u0085b", "a\u0007b",
        "a\u2028www.x.example", "a\u2029b", "a\u2028b://c"
    })
    void should_refuse_links_control_characters_and_separators(String value) {
        assertThat(value.matches(LeadRequest.ONE_LINE_NO_LINK)).isFalse();
    }
}
