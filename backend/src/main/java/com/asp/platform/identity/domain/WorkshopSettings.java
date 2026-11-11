package com.asp.platform.identity.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.time.ZoneId;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.CountryCodes;
import com.asp.platform.shared.PortugueseNif;

/// How a workshop presents itself and what it charges by default.
///
/// @param name            the workshop's trading name
/// @param nif             Portuguese tax number, 9 digits, when known
/// @param country         ISO 3166-1 alpha-2 code, for example "PT"
/// @param timezone        IANA zone id, for example "Europe/Lisbon"; "this month" is counted in it
/// @param defaultVatRate  VAT percentage suggested for new lines, 0 to 100 with at most 2 decimals
/// @param labourRateCents hourly labour rate in cents, suggested for new labour lines
/// @param quoteValidDays  days a quote sent to a customer stays open, [#MIN_QUOTE_VALID_DAYS] to
///                        [#MAX_QUOTE_VALID_DAYS]
/// @param deliveryChecklistRequired when on, a work order cannot be delivered while required items of its delivery
///                        checklist are open
/// @param labourCostPerHourCents what one hour of labour costs the workshop, in cents, for the margin of the reports
///                        (not the price: that is `labourRateCents`); null when the owner has not set it, 0 is a value
/// @param retentionMonths the window of the retention KPI, [#MIN_RETENTION_MONTHS] to [#MAX_RETENTION_MONTHS]
public record WorkshopSettings(String name, @Nullable String nif, String country, String timezone,
        BigDecimal defaultVatRate, long labourRateCents, int quoteValidDays, boolean deliveryChecklistRequired,
        @Nullable Long labourCostPerHourCents, int retentionMonths) {

    /// Settings without the delivery checklist gate (off).
    public WorkshopSettings(String name, @Nullable String nif, String country, String timezone,
            BigDecimal defaultVatRate, long labourRateCents, int quoteValidDays) {
        this(name, nif, country, timezone, defaultVatRate, labourRateCents, quoteValidDays, false);
    }

    /// Settings without a labour cost and with the default retention window.
    public WorkshopSettings(String name, @Nullable String nif, String country, String timezone,
            BigDecimal defaultVatRate, long labourRateCents, int quoteValidDays, boolean deliveryChecklistRequired) {
        this(name, nif, country, timezone, defaultVatRate, labourRateCents, quoteValidDays,
                deliveryChecklistRequired, null, DEFAULT_RETENTION_MONTHS);
    }

    public static final int MIN_QUOTE_VALID_DAYS = 1;
    public static final int MAX_QUOTE_VALID_DAYS = 90;
    public static final int DEFAULT_QUOTE_VALID_DAYS = 15;
    public static final int MIN_RETENTION_MONTHS = 1;
    public static final int MAX_RETENTION_MONTHS = 60;
    public static final int DEFAULT_RETENTION_MONTHS = 12;
    /// EUR 10 000 an hour: a ceiling that catches a typed-in euro value, and keeps hours times cost far from overflow.
    public static final long MAX_LABOUR_COST_PER_HOUR_CENTS = 1_000_000L;

    private static final Pattern REGION_ID = Pattern.compile("[A-Za-z]+(/[A-Za-z0-9_+-]+)+");
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
        if (quoteValidDays < MIN_QUOTE_VALID_DAYS || quoteValidDays > MAX_QUOTE_VALID_DAYS) {
            throw new IllegalArgumentException("A quote is valid for " + MIN_QUOTE_VALID_DAYS + " to "
                    + MAX_QUOTE_VALID_DAYS + " days");
        }
        if (labourCostPerHourCents != null
                && (labourCostPerHourCents < 0 || labourCostPerHourCents > MAX_LABOUR_COST_PER_HOUR_CENTS)) {
            throw new IllegalArgumentException("Labour cost per hour is 0 to " + MAX_LABOUR_COST_PER_HOUR_CENTS
                    + " cents");
        }
        if (retentionMonths < MIN_RETENTION_MONTHS || retentionMonths > MAX_RETENTION_MONTHS) {
            throw new IllegalArgumentException("The retention window is " + MIN_RETENTION_MONTHS + " to "
                    + MAX_RETENTION_MONTHS + " months");
        }
    }

    /// Checks a Portuguese NIF (check digit and first digit). Called when settings are written, not when they are
    /// read, so a NIF stored before the check existed stays readable until the owner edits the settings.
    ///
    /// @throws com.asp.platform.shared.InvalidNifException when the country is PT and the NIF is not valid
    public WorkshopSettings checkNif() {
        PortugueseNif.check(nif, country);
        return this;
    }

    /// Release 1 sells in Portugal only, so new workshops start with Portuguese defaults.
    public static WorkshopSettings portugueseDefaults(String name, @Nullable String nif) {
        return initial(name, nif, "PT", "Europe/Lisbon");
    }

    /// Settings of a workshop the platform admin registers: the owner sets VAT and labour rate later.
    /// The 23% VAT default is Portugal's standard rate, the first market.
    public static WorkshopSettings initial(String name, @Nullable String nif, String country, String timezone) {
        return new WorkshopSettings(name, nif, country, timezone, new BigDecimal("23.00"), 0,
                DEFAULT_QUOTE_VALID_DAYS);
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /// Region ids such as "Europe/Lisbon" (and "UTC" or "GMT"), nothing else: no offsets such as "+01:00" or
    /// "UTC+01:00" and no short names such as "CET". The reports hand the zone to PostgreSQL's `AT TIME ZONE`, where an
    /// offset id would mean a POSIX offset with the opposite sign. The pattern is the one of the check constraint
    /// `tenant_timezone_region` (V23).
    public static boolean isZoneId(String id) {
        return (REGION_ID.matcher(id).matches() || id.equals("UTC") || id.equals("GMT"))
                && !LegacyZoneIds.NOT_IN_POSTGRESQL.contains(id)
                && ZoneId.getAvailableZoneIds().contains(id);
    }
}
