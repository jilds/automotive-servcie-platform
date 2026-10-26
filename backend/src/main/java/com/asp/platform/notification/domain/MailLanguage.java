package com.asp.platform.notification.domain;

import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// The language of a mail: the one the person chose on screen when it is known and supported, else the workshop
/// country's (Portugal: pt-PT, Brazil: pt-BR), else English. The same three languages as the UI.
public final class MailLanguage {

    static final Set<String> SUPPORTED = Set.of("pt-PT", "en", "pt-BR");

    private MailLanguage() {
    }

    /// @param chosen  a language tag from the page (`pt-PT`, `en`, `pt-BR`), or null
    /// @param country the workshop's two-letter country code
    public static Locale of(@Nullable String chosen, String country) {
        if (chosen != null && SUPPORTED.contains(chosen)) {
            return Locale.forLanguageTag(chosen);
        }
        return switch (country.toUpperCase(Locale.ROOT)) {
            case "PT" -> Locale.forLanguageTag("pt-PT");
            case "BR" -> Locale.forLanguageTag("pt-BR");
            default -> Locale.ENGLISH;
        };
    }
}
