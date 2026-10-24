package com.asp.platform.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortugueseNifTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "123456789", // person
            "509999999", // company
            "999999990", // irregular entity, check digit 0 (remainder 0)
            "501442600", // company, remainder 1 also gives check digit 0
            "286999994", // person, series starting with 2
            "450000001", // non-resident person (45)
            "710000006", // estate (71)
    })
    void should_accept_valid_nifs(String nif) {
        assertThat(PortugueseNif.isValid(nif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123456780", // wrong check digit
            "509999990", // wrong check digit
            "400000006", // 4 is only valid as 45
            "700000004", // 70 is valid, check digit wrong (3)
            "000000000", // no NIF starts with 0
            "12345678",  // 8 digits
            "1234567890", // 10 digits
            "12345678a",
            "",
    })
    void should_reject_invalid_nifs(String nif) {
        assertThat(PortugueseNif.isValid(nif)).isFalse();
    }

    @Test
    void should_check_only_portuguese_nifs() {
        assertThat(PortugueseNif.isAcceptable("123456780", "ES")).isTrue();
        assertThat(PortugueseNif.isAcceptable("123456780", "pt")).isFalse();
        assertThat(PortugueseNif.isAcceptable(null, "PT")).isTrue();
        assertThat(PortugueseNif.isAcceptable("  ", "PT")).isTrue();
        assertThat(PortugueseNif.isAcceptable(" 123456789 ", "PT")).isTrue();

        assertThatThrownBy(() -> PortugueseNif.check("123456780", "PT"))
                .isInstanceOfSatisfying(InvalidNifException.class, e -> {
                    assertThat(e.code()).isEqualTo("invalid-nif");
                    assertThat(e.field()).isEqualTo("nif");
                });
    }
}
