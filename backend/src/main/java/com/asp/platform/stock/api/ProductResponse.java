package com.asp.platform.stock.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.application.ProductView;
import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.ProductDetails;

/// A product with its stock. `available` = `onHand` − `reserved`; `low` when available is below `minStock`.
/// Mechanics get the name, SKU, brand, unit, bin and available quantity only: cost, prices and the other stock
/// figures are null. `version` goes back with the next edit; stock movements do not change it.
record ProductResponse(UUID id, String sku, String name, @Nullable String brand, String unit,
        @Nullable Long costCents, @Nullable Long salePriceCents, @Nullable BigDecimal vatRate,
        @Nullable BigDecimal minStock, @Nullable String bin, @Nullable BigDecimal onHand,
        @Nullable BigDecimal reserved, BigDecimal available, @Nullable Boolean low, boolean active,
        boolean listedOnline, long version) {

    static ProductResponse of(ProductView view, boolean full) {
        Product p = view.product();
        ProductDetails d = p.details();
        return new ProductResponse(p.id(), d.sku(), d.name(), d.brand(), d.unit(),
                full ? d.costCents() : null,
                full ? d.salePriceCents() : null,
                full ? d.vatRate() : null,
                full ? d.minStock() : null,
                d.bin(),
                full ? view.level().onHand() : null,
                full ? view.level().reserved() : null,
                view.available(),
                full ? view.low() : null,
                d.active(), d.listedOnline(), p.version());
    }
}
