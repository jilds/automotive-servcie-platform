package com.asp.platform.identity.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.WorkshopSettings;
import com.asp.platform.shared.IsoCountry;

record WorkshopSettingsRequest(
        @NotBlank String name,
        @Nullable @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
        @NotBlank @IsoCountry String country,
        @NotBlank @TimeZoneId String timezone,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal defaultVatRate,
        @NotNull @PositiveOrZero Long labourRateCents) {

    WorkshopSettings toSettings() {
        return new WorkshopSettings(name, nif, country, timezone, defaultVatRate, labourRateCents);
    }
}
