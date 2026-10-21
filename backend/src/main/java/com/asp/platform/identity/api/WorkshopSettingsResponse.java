package com.asp.platform.identity.api;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.WorkshopSettings;

record WorkshopSettingsResponse(String name, @Nullable String nif, String country, String timezone,
        BigDecimal defaultVatRate, long labourRateCents) {

    static WorkshopSettingsResponse of(WorkshopSettings s) {
        return new WorkshopSettingsResponse(s.name(), s.nif(), s.country(), s.timezone(), s.defaultVatRate(),
                s.labourRateCents());
    }
}
