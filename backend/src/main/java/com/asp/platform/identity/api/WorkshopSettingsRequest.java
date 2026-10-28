package com.asp.platform.identity.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.WorkshopSettings;
import com.asp.platform.shared.IsoCountry;
import com.asp.platform.shared.ValidNif;

/// A Portuguese workshop (country PT) needs a valid NIF check digit. `version` is the one the owner read; a different
/// stored version answers 409 `stale-data`. `quoteValidDays` is optional: left out, the stored value stays.
@ValidNif
record WorkshopSettingsRequest(
        @NotBlank String name,
        @Nullable @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
        @NotBlank @IsoCountry String country,
        @NotBlank @TimeZoneId String timezone,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal defaultVatRate,
        @NotNull @PositiveOrZero Long labourRateCents,
        @Nullable @Min(WorkshopSettings.MIN_QUOTE_VALID_DAYS) @Max(WorkshopSettings.MAX_QUOTE_VALID_DAYS)
        Integer quoteValidDays,
        @NotNull Long version) implements ValidNif.Subject {

    /// @param stored the workshop's current settings, for the fields the request leaves out
    WorkshopSettings toSettings(WorkshopSettings stored) {
        Integer days = quoteValidDays;
        return new WorkshopSettings(name, nif, country, timezone, defaultVatRate, labourRateCents,
                days == null ? stored.quoteValidDays() : days);
    }

    @Override
    public @Nullable String nifCountry() {
        return country;
    }
}
