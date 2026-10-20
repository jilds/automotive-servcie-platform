package com.asp.platform.customer.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// A postal address in the shape certified invoicing APIs expect.
///
/// @param street     street and number, for example "Rua Augusta, 120"
/// @param detail     floor or door, for example "2º Esq."
/// @param postalCode Portuguese format NNNN-NNN when the country is PT
/// @param locality   for example "Lisboa"
/// @param country    ISO 3166-1 alpha-2 code, PT by default
public record Address(String street, @Nullable String detail, String postalCode, String locality, String country) {

    public static final String PORTUGAL = "PT";
    private static final Pattern PT_POSTAL_CODE = Pattern.compile("\\d{4}-\\d{3}");
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    public Address {
        street = required(street, "Street");
        postalCode = required(postalCode, "Postal code");
        locality = required(locality, "Locality");
        detail = detail == null || detail.isBlank() ? null : detail.trim();
        country = country.isBlank() ? PORTUGAL : country.trim().toUpperCase(Locale.ROOT);
        if (!COUNTRY_CODE.matcher(country).matches()) {
            throw new IllegalArgumentException("Country must be a two-letter code");
        }
        if (PORTUGAL.equals(country) && !PT_POSTAL_CODE.matcher(postalCode).matches()) {
            throw new IllegalArgumentException("Portuguese postal codes look like 1100-053");
        }
    }

    private static String required(String value, String name) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
