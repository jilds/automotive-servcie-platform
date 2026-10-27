package com.asp.platform.shop.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shop.domain.Availability;
import com.asp.platform.shop.domain.ShopOrderLine;
import com.asp.platform.stock.OnlineProduct;

/// A product as the public page shows it: price with VAT and availability, never the quantity in stock.
public record PublicProduct(UUID id, String sku, String name, @Nullable String brand, String unit,
        long unitPriceWithVatCents, Availability availability) {

    static PublicProduct of(OnlineProduct p) {
        return new PublicProduct(p.id(), p.sku(), p.name(), p.brand(), p.unit(),
                ShopOrderLine.withVat(p.salePriceCents(), p.vatRate()), Availability.of(p.available(), p.minStock()));
    }
}
