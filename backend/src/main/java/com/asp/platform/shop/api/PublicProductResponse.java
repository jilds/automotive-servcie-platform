package com.asp.platform.shop.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.application.PublicProduct;
import com.asp.platform.shop.domain.Availability;

/// A product on the public page: price with VAT, availability as a word, never the quantity.
record PublicProductResponse(UUID id, String sku, String name, @Nullable String brand, String unit,
        long unitPriceWithVatCents, Availability availability) {

    static PublicProductResponse of(PublicProduct p) {
        return new PublicProductResponse(p.id(), p.sku(), p.name(), p.brand(), p.unit(), p.unitPriceWithVatCents(),
                p.availability());
    }
}
