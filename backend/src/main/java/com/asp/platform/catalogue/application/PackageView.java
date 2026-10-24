package com.asp.platform.catalogue.application;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.catalogue.domain.PackageItemKind;
import com.asp.platform.catalogue.domain.ServicePackage;

/// A package with the name and price of each of its services and products.
///
/// @param listPriceCents what the items cost on their own (services at their effective price, products at their sale
///                       price, times the quantity), to show the saving
public record PackageView(ServicePackage servicePackage, List<Item> items, long listPriceCents) {

    /// @param id     the service's or the product's id
    /// @param sku    the product's SKU; null for a service
    /// @param active false when the service is no longer offered or the product no longer sold (the package still
    ///               holds it)
    public record Item(PackageItemKind kind, UUID id, String name, @Nullable String sku, int quantity,
            long unitPriceCents, boolean active) {
    }
}
