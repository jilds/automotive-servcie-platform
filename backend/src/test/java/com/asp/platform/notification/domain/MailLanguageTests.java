package com.asp.platform.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class MailLanguageTests {

    @Test
    void should_use_the_language_chosen_on_the_page() {
        assertThat(MailLanguage.of("en", "PT")).isEqualTo(Locale.ENGLISH);
        assertThat(MailLanguage.of("pt-BR", "PT").toLanguageTag()).isEqualTo("pt-BR");
    }

    @Test
    void should_fall_back_to_the_workshop_country() {
        assertThat(MailLanguage.of(null, "PT").toLanguageTag()).isEqualTo("pt-PT");
        assertThat(MailLanguage.of(null, "br").toLanguageTag()).isEqualTo("pt-BR");
        assertThat(MailLanguage.of(null, "ES")).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void should_ignore_a_language_the_ui_does_not_have() {
        assertThat(MailLanguage.of("fr", "PT").toLanguageTag()).isEqualTo("pt-PT");
    }
}
