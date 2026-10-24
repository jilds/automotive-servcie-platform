package com.asp.platform.catalogue.domain;

import java.util.UUID;

/// A stock product as a package shows and prices it.
///
/// @param salePriceCents sale price before VAT per unit, in cents
/// @param active         false when the product is no longer sold: it stays in packages that hold it, but cannot be
///                       added to a package and a package holding it cannot go onto a work order
public record PackageProduct(UUID id, String sku, String name, long salePriceCents, boolean active) {
}
