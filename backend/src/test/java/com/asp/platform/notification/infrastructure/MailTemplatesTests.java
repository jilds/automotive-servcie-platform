package com.asp.platform.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.asp.platform.notification.MailMessage;
import com.asp.platform.notification.MailTemplate;

/// Every template renders in every language, with the subject, the variables and no unresolved text key.
class MailTemplatesTests {

    private static final Map<String, Object> MODEL = model("name", "Ana Silva", "workshop", "Oficina Azul",
            "number", "1234", "when", "sexta-feira, 30 de outubro de 2026 09:00", "link",
            "https://app.asp.test/w/oficina-azul/booking/TOKEN", "expires", "29/10/2026 10:00", "firstOwner", true,
            "reason", "Sem peças", "vehicleType", "BOTH", "phone", "912345678", "email", "", "district", "Lisboa",
            "hasMessage", true, "total", "29,52 €", "until", "sexta-feira, 30 de outubro de 2026 18:00",
            "pickupNote", "Das 9h às 18h");

    private final MailTemplates templates = new MailTemplateConfig().mailTemplates();

    private static Map<String, Object> model(Object... keysAndValues) {
        Map<String, Object> model = new java.util.HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            model.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return model;
    }

    static Stream<Arguments> everyTemplateAndLanguage() {
        return Stream.of(MailTemplate.values()).flatMap(t -> Stream.of("pt-PT", "en", "pt-BR")
                .map(lang -> Arguments.of(t, Locale.forLanguageTag(lang))));
    }

    @ParameterizedTest
    @MethodSource("everyTemplateAndLanguage")
    void should_render_subject_text_and_html(MailTemplate template, Locale locale) {
        MailTemplates.Rendered mail = templates.render(new MailMessage("ana@example.pt", template, locale, MODEL));

        assertThat(mail.subject()).contains("Oficina Azul").doesNotContain("{", "??");
        assertThat(mail.text()).contains("Ana Silva").doesNotContain("??", "[(", "{0}");
        assertThat(mail.html()).contains("Ana Silva", "lang=\"" + locale.toLanguageTag() + "\"")
                .doesNotContain("??", "{0}");
        if (template.file().startsWith("booking-") || template.file().startsWith("shop-")) {
            assertThat(mail.subject()).contains("1234");
        }
    }

    @ParameterizedTest
    @MethodSource("everyTemplateAndLanguage")
    void should_show_the_link_only_where_the_mail_has_one(MailTemplate template, Locale locale) {
        MailTemplates.Rendered mail = templates.render(new MailMessage("ana@example.pt", template, locale, MODEL));

        boolean hasLink = template == MailTemplate.ACTIVATION || template == MailTemplate.BOOKING_RECEIVED
                || template == MailTemplate.LEAD_RECEIVED || template == MailTemplate.SHOP_ORDER_RECEIVED;
        assertThat(mail.text().contains("https://app.asp.test/")).isEqualTo(hasLink);
        assertThat(mail.html().contains("href=\"https://app.asp.test/")).isEqualTo(hasLink);
    }

    @ParameterizedTest
    @MethodSource("everyTemplateAndLanguage")
    void should_escape_names_in_the_html(MailTemplate template, Locale locale) {
        MailTemplates.Rendered mail = templates.render(new MailMessage("ana@example.pt", template, locale,
                model("name", "<b>Ana</b>", "workshop", "Oficina & Filhos", "number", "1", "when", "x",
                        "link", "https://app.asp.test/x", "expires", "x", "firstOwner", false, "reason", "",
                        "vehicleType", "CAR", "phone", "", "email", "", "district", "", "hasMessage", false,
                        "total", "1 €", "until", "x", "pickupNote", "<i>x</i>")));

        assertThat(mail.html()).doesNotContain("<b>Ana</b>").contains("&lt;b&gt;Ana&lt;/b&gt;");
    }

    @org.junit.jupiter.api.Test
    void should_write_portuguese_dates_and_texts_for_a_portuguese_workshop() {
        MailTemplates.Rendered mail = templates.render(new MailMessage("ana@example.pt",
                MailTemplate.BOOKING_DECLINED, Locale.forLanguageTag("pt-PT"), MODEL));

        assertThat(mail.subject()).isEqualTo("Marcação n.º 1234 em Oficina Azul não aceite");
        assertThat(mail.text()).contains("Olá Ana Silva,", "Motivo: Sem peças");
    }

    @org.junit.jupiter.api.Test
    void should_tell_the_customer_when_and_how_to_collect_a_ready_order() {
        MailTemplates.Rendered mail = templates.render(new MailMessage("ana@example.pt",
                MailTemplate.SHOP_ORDER_READY, Locale.forLanguageTag("pt-PT"), MODEL));

        assertThat(mail.subject()).isEqualTo("Encomenda n.º 1234 em Oficina Azul pronta");
        assertThat(mail.text()).contains("pague ao balcão até sexta-feira, 30 de outubro de 2026 18:00",
                "Nota da oficina: Das 9h às 18h");
        MailTemplates.Rendered received = templates.render(new MailMessage("ana@example.pt",
                MailTemplate.SHOP_ORDER_RECEIVED, Locale.ENGLISH, MODEL));
        assertThat(received.subject()).isEqualTo("Order no. 1234 at Oficina Azul");
        assertThat(received.text()).contains("(29,52 €)", "See or cancel your order");
    }
}
