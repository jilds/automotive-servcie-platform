package com.asp.platform.shared;

import java.util.Locale;
import java.util.Set;

/// ISO 3166-1 alpha-2 country codes, the one check used by every feature.
public final class CountryCodes {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private CountryCodes() {
    }

    /// True for an upper-case ISO 3166-1 alpha-2 code such as "PT". Callers normalise case first.
    public static boolean isIso(String code) {
        return ISO_COUNTRIES.contains(code);
    }
}
