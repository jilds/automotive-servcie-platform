package com.asp.platform.identity.api;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.WorkshopSettings;

/// @param labourCostPerHourCents what an hour of labour costs the workshop: **owner only**, null for anyone else
/// @param version the workshop's version, sent back with the next settings or branding change
record WorkshopSettingsResponse(String name, @Nullable String nif, String country, String timezone,
        BigDecimal defaultVatRate, long labourRateCents, int quoteValidDays,
        boolean deliveryChecklistRequired, @Nullable Long labourCostPerHourCents, int retentionMonths,
        long version) {

    /// @param owner whether the caller is the owner: the labour cost per hour (a cost, not a price) is shown to nobody else
    static WorkshopSettingsResponse of(Tenant tenant, boolean owner) {
        WorkshopSettings s = tenant.settings();
        return new WorkshopSettingsResponse(s.name(), s.nif(), s.country(), s.timezone(), s.defaultVatRate(),
                s.labourRateCents(), s.quoteValidDays(),
                s.deliveryChecklistRequired(), owner ? s.labourCostPerHourCents() : null, s.retentionMonths(), tenant.version());
    }
}
