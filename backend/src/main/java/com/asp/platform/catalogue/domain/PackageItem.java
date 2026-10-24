package com.asp.platform.catalogue.domain;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A service or a stock product inside a package, with how many of it (for example two wheel alignments, or four
/// litres of oil). Exactly one of `serviceItemId` and `productId` is set.
public record PackageItem(@Nullable UUID serviceItemId, @Nullable UUID productId, int quantity) {

    public static final int MAX_QUANTITY = 99;

    public PackageItem {
        if ((serviceItemId == null) == (productId == null)) {
            throw new IllegalArgumentException("A package item is either a service or a product");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QUANTITY);
        }
    }

    /// A service of the catalogue.
    public PackageItem(UUID serviceItemId, int quantity) {
        this(serviceItemId, null, quantity);
    }

    public static PackageItem service(UUID serviceItemId, int quantity) {
        return new PackageItem(serviceItemId, null, quantity);
    }

    public static PackageItem product(UUID productId, int quantity) {
        return new PackageItem(null, productId, quantity);
    }

    public PackageItemKind kind() {
        return productId == null ? PackageItemKind.SERVICE : PackageItemKind.PRODUCT;
    }

    /// The service's or the product's id, whichever the item holds.
    public UUID itemId() {
        return Objects.requireNonNullElseGet(serviceItemId, () -> Objects.requireNonNull(productId));
    }

    /// Two items with the same key are the same thing and are merged.
    public Key key() {
        return new Key(kind(), itemId());
    }

    public record Key(PackageItemKind kind, UUID id) {
    }
}
