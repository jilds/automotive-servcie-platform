package com.asp.platform.catalogue.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.application.ServiceView;
import com.asp.platform.catalogue.domain.ServiceDetails;
import com.asp.platform.catalogue.domain.ServiceItem;
import com.asp.platform.catalogue.domain.ServicePricing;

/// Prices and VAT are null for callers who may not see prices (mechanics). `priceCents` is the stored fixed price;
/// `effectivePriceCents` is what the service costs now (for `HOURLY`, standard hours times the current labour rate).
/// `version` goes back with the next edit.
record ServiceResponse(UUID id, String name, @Nullable String description, @Nullable String category,
        BigDecimal standardHours, ServicePricing pricing, @Nullable Long priceCents,
        @Nullable Long effectivePriceCents, @Nullable BigDecimal vatRate, int durationMinutes, boolean active,
        boolean listedOnline, long version) {

    static ServiceResponse of(ServiceView view, boolean prices) {
        ServiceItem item = view.item();
        ServiceDetails d = item.details();
        return new ServiceResponse(item.id(), d.name(), d.description(), d.category(), d.standardHours(),
                d.pricing(), prices ? d.priceCents() : null, prices ? view.effectivePriceCents() : null,
                prices ? d.vatRate() : null, d.durationMinutes(), d.active(), d.listedOnline(), item.version());
    }
}
