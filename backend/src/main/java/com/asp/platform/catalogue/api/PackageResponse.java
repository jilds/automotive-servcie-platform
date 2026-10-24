package com.asp.platform.catalogue.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.application.PackageView;
import com.asp.platform.catalogue.domain.PackageDetails;
import com.asp.platform.catalogue.domain.PackageItemKind;

/// A package with its services and products. `listPriceCents` is what the items cost on their own (to show the
/// saving); `version` goes back with the next edit.
record PackageResponse(UUID id, String name, @Nullable String description, long priceCents, BigDecimal vatRate,
        long listPriceCents, boolean active, boolean listedOnline, List<Item> items, long version) {

    /// `serviceId` is set for a SERVICE, `productId` and `sku` for a PRODUCT; `unitPriceCents` is the service's
    /// effective price or the product's sale price.
    record Item(PackageItemKind kind, @Nullable UUID serviceId, @Nullable UUID productId, String name,
            @Nullable String sku, int quantity, long unitPriceCents, boolean active) {

        static Item of(PackageView.Item i) {
            boolean service = i.kind() == PackageItemKind.SERVICE;
            return new Item(i.kind(), service ? i.id() : null, service ? null : i.id(), i.name(), i.sku(),
                    i.quantity(), i.unitPriceCents(), i.active());
        }
    }

    static PackageResponse of(PackageView view) {
        PackageDetails d = view.servicePackage().details();
        return new PackageResponse(view.servicePackage().id(), d.name(), d.description(), d.priceCents(), d.vatRate(),
                view.listPriceCents(), d.active(), d.listedOnline(), view.items().stream().map(Item::of).toList(),
                view.servicePackage().version());
    }
}
