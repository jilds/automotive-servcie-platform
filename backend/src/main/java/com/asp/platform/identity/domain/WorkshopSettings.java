package com.asp.platform.identity.domain;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.CountryCodes;

/// How a workshop presents itself and what it charges by default.
///
/// @param name            the workshop's trading name
/// @param nif             Portuguese tax number, 9 digits, when known
/// @param country         ISO 3166-1 alpha-2 code, for example "PT"
/// @param timezone        IANA zone id, for example "Europe/Lisbon"; "this month" is counted in it
/// @param defaultVatRate  VAT percentage suggested for new lines, 0 to 100 with at most 2 decimals
/// @param labourRateCents hourly labour rate in cents, suggested for new labour lines
public record WorkshopSettings(String name, @Nullable String nif, String country, String timezone,
        BigDecimal defaultVatRate, long labourRateCents) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int VAT_DECIMALS = 2;

    public WorkshopSettings {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Workshop name is required");
        }
        name = name.trim();
        nif = nif == null || nif.isBlank() ? null : nif.trim();
        country = country.trim().toUpperCase(Locale.ROOT);
        if (!CountryCodes.isIso(country)) {
            throw new IllegalArgumentException("Country must be an ISO 3166-1 alpha-2 code");
        }
        timezone = timezone.trim();
        if (!isZoneId(timezone)) {
            throw new IllegalArgumentException("Unknown time zone " + timezone);
        }
        if (defaultVatRate.signum() < 0 || defaultVatRate.compareTo(HUNDRED) > 0
                || defaultVatRate.stripTrailingZeros().scale() > VAT_DECIMALS) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100 with at most 2 decimals");
        }
        defaultVatRate = defaultVatRate.setScale(VAT_DECIMALS);
        if (labourRateCents < 0) {
            throw new IllegalArgumentException("Labour rate cannot be negative");
        }
    }

    /// Release 1 sells in Portugal only, so new workshops start with Portuguese defaults.
    public static WorkshopSettings portugueseDefaults(String name, @Nullable String nif) {
        return new WorkshopSettings(name, nif, "PT", "Europe/Lisbon", new BigDecimal("23.00"), 0);
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /// Region ids such as "Europe/Lisbon" and fixed ids such as "UTC" or "+01:00".
    public static boolean isZoneId(String id) {
        try {
            ZoneId.of(id);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
