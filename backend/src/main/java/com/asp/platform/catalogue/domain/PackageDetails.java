package com.asp.platform.catalogue.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/// A package of services and stock products sold together for one price, such as "Annual service" (oil change,
/// filters, inspection, plus the oil filter and the oil themselves).
///
/// @param priceCents the package price before VAT, in cents; may be lower than the sum of its items
/// @param vatRate    VAT percentage of the whole package, 0 to 100
/// @param items      at least one service or product; the same service or product listed twice is merged into one item
public record PackageDetails(String name, @Nullable String description, long priceCents, BigDecimal vatRate,
        boolean active, boolean listedOnline, List<PackageItem> items) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PackageDetails {
        name = Text.required(name, "Name", ServiceDetails.MAX_NAME);
        description = Text.optional(description, "Description", ServiceDetails.MAX_DESCRIPTION);
        if (priceCents < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (vatRate.signum() < 0 || vatRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        items = merged(items);
        if (items.isEmpty()) {
            throw new PackageEmptyException();
        }
    }

    /// Keeps the order of first appearance and adds up the quantities of repeated services or products.
    ///
    /// @throws IllegalArgumentException when a merged quantity goes over [PackageItem#MAX_QUANTITY]
    private static List<PackageItem> merged(List<PackageItem> items) {
        Map<PackageItem.Key, PackageItem> byKey = new LinkedHashMap<>();
        items.forEach(item -> byKey.merge(item.key(), item, (a, b) ->
                new PackageItem(a.serviceItemId(), a.productId(), a.quantity() + b.quantity())));
        return List.copyOf(new ArrayList<>(byKey.values()));
    }
}
