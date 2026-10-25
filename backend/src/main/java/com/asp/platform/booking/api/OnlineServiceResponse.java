package com.asp.platform.booking.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.ServiceOffer;

/// A service the customer may book online. Money in cents; `priceCents` before VAT.
record OnlineServiceResponse(UUID id, String name, @Nullable String description, @Nullable String category,
        int durationMinutes, long priceCents, BigDecimal vatRate, long priceWithVatCents) {

    static OnlineServiceResponse of(ServiceOffer s) {
        return new OnlineServiceResponse(s.id(), s.name(), s.description(), s.category(), s.durationMinutes(),
                s.priceCents(), s.vatRate(), s.priceWithVatCents());
    }
}
