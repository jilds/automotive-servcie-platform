package com.asp.platform.catalogue.infrastructure;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.asp.platform.catalogue.domain.PackageProduct;
import com.asp.platform.catalogue.domain.PackageProducts;
import com.asp.platform.stock.StockItem;
import com.asp.platform.stock.StockReservations;

/// The workshop's products, read through the stock module's public API (row-level security keeps them in the
/// workshop). Not-found and no-longer-sold errors are the stock module's (`product-not-found`, `product-inactive`).
@Component
class StockPackageProducts implements PackageProducts {

    private final StockReservations stock;

    StockPackageProducts(StockReservations stock) {
        this.stock = stock;
    }

    @Override
    public Map<UUID, PackageProduct> find(Collection<UUID> ids) {
        return stock.items(ids).values().stream()
                .collect(Collectors.toUnmodifiableMap(StockItem::id, StockPackageProducts::product));
    }

    @Override
    public PackageProduct requireForSale(UUID id) {
        return product(stock.item(id));
    }

    private static PackageProduct product(StockItem item) {
        return new PackageProduct(item.id(), item.sku(), item.name(), item.salePriceCents(), item.active());
    }
}
