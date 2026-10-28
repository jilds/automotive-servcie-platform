package com.asp.platform.identity.api;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.WorkshopSettings;

/// @param version the workshop's version, sent back with the next settings or branding change
record WorkshopSettingsResponse(String name, @Nullable String nif, String country, String timezone,
        BigDecimal defaultVatRate, long labourRateCents, int quoteValidDays, long version) {

    static WorkshopSettingsResponse of(Tenant tenant) {
        WorkshopSettings s = tenant.settings();
        return new WorkshopSettingsResponse(s.name(), s.nif(), s.country(), s.timezone(), s.defaultVatRate(),
                s.labourRateCents(), s.quoteValidDays(), tenant.version());
    }
}
