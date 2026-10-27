package com.asp.platform.shop.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// The customer's name goes into the e-mails: one line, without links.
class PublicShopOrderRequestTests {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Ana Silva", "Auto São João, Lda."})
    void should_accept_a_plain_name(String name) {
        assertThat(nameErrors(name)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Visite https://x.example", "WWW.x.pt", "a<b", "a\nb", "a\u0085b", "a\u2028b",
        "a\u2029www.x.pt"})
    void should_refuse_links_control_characters_and_separators(String name) {
        assertThat(nameErrors(name)).isPositive();
    }

    private static long nameErrors(String name) {
        PublicShopOrderRequest request = new PublicShopOrderRequest(
                List.of(new PublicShopOrderRequest.Line(UUID.randomUUID(), 1)), name, "912345678", null, null, true,
                false, "", "pt-PT");
        return VALIDATOR.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("name"))
                .count();
    }
}
