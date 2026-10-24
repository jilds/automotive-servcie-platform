package com.asp.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class BrandingRulesTests {

    @Test
    void should_compute_wcag_contrast() {
        assertThat(BrandColor.BLACK.contrastWith(BrandColor.WHITE)).isCloseTo(21.0, within(0.01));
        assertThat(new BrandColor("#777777").contrastWith(BrandColor.WHITE)).isCloseTo(4.48, within(0.01));
    }

    @ParameterizedTest
    @CsvSource({
            "#0B3D91, #ffffff", // dark blue: white reaches AA
            "#767676, #ffffff", // the lightest grey white still reaches 4.5:1 on
            "#777777, #000000", // a shade lighter: black
            "#ffd43b, #000000",
            "#f2b200, #000000", // ASP amber
            "#b42318, #ffffff",
    })
    void should_pick_the_button_text_colour_that_reaches_aa(String primary, String text) {
        BrandColor color = new BrandColor(primary);
        assertThat(color.textColor().hex()).isEqualTo(text);
        assertThat(color.contrastWith(color.textColor())).isGreaterThanOrEqualTo(BrandColor.MIN_TEXT_CONTRAST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"red", "#fff", "#12345g", "0b3d91", "#0b3d911"})
    void should_reject_colours_that_are_not_rrggbb(String value) {
        assertThatThrownBy(() -> new BrandColor(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Oficina São João, Lda.|oficina-sao-joao-lda",
            "  AUTO  Reparações  |auto-reparacoes",
            "!!!|workshop",
            "Garagem 24/7|garagem-24-7",
    })
    void should_make_a_slug_from_a_name(String name, String slug) {
        assertThat(Slug.fromName(name).value()).isEqualTo(slug);
    }

    @Test
    void should_keep_slugs_short_and_numbered() {
        Slug longOne = Slug.fromName("a".repeat(60));
        assertThat(longOne.value()).hasSizeLessThanOrEqualTo(Slug.MAX_LENGTH);
        assertThat(longOne.numbered(12).value()).hasSizeLessThanOrEqualTo(Slug.MAX_LENGTH).endsWith("-12");
        assertThat(Slug.isValid("a")).isTrue();
        assertThat(Slug.isValid("-a")).isFalse();
        assertThat(Slug.isValid("a--b")).isFalse();
        assertThat(Slug.isValid("A")).isFalse();
        assertThat(Slug.isValid("a".repeat(41))).isFalse();
    }

    @Test
    void should_trim_the_display_name_and_cap_it() {
        assertThat(new Branding("  Oficina  ", null, null).displayName()).isEqualTo("Oficina");
        assertThat(new Branding(" ", null, null).displayName()).isNull();
        assertThatThrownBy(() -> new Branding("x".repeat(81), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_use_an_activation_token_once_and_only_for_72_hours() {
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        ActivationToken token = ActivationToken.issue(UUID.randomUUID(), UUID.randomUUID(), "raw-token", now);

        assertThat(token.tokenHash()).hasSize(64).isEqualTo(ActivationToken.hash("raw-token")).doesNotContain("raw");
        assertThat(Duration.between(token.createdAt(), token.expiresAt())).isEqualTo(Duration.ofHours(72));
        token.requireUsable(now.plus(Duration.ofHours(71)));
        assertThatThrownBy(() -> token.requireUsable(now.plus(Duration.ofHours(72))))
                .isInstanceOf(ActivationExpiredException.class);
        assertThatThrownBy(() -> token.used(now).requireUsable(now))
                .isInstanceOf(ActivationInvalidException.class);
    }

    @Test
    void should_accept_passwords_of_12_to_72_bytes() {
        assertThat(Password.isAcceptable("x".repeat(11))).isFalse();
        assertThat(Password.isAcceptable("x".repeat(12))).isTrue();
        assertThat(Password.isAcceptable("x".repeat(72))).isTrue();
        assertThat(Password.isAcceptable("x".repeat(73))).isFalse();
        assertThat(Password.isAcceptable(" ".repeat(12))).isFalse();
    }

    @Test
    void should_detect_the_logo_type_from_the_bytes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};
        Instant now = Instant.EPOCH;
        assertThat(Logo.upload(png, now).contentType()).isEqualTo(Logo.PNG);
        assertThat(Logo.upload(webp, now).contentType()).isEqualTo(Logo.WEBP);
        assertThat(Logo.upload("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(), now).contentType())
                .isEqualTo(Logo.SVG);
        assertThat(Logo.upload(png, now).sha256()).hasSize(64);
        assertThatThrownBy(() -> Logo.upload(new byte[0], now))
                .isInstanceOfSatisfying(InvalidLogoException.class,
                        e -> assertThat(e.code()).isEqualTo(InvalidLogoException.UNSUPPORTED));
    }
}
