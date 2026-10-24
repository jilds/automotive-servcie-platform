package com.asp.platform.catalogue;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A line to put on a work order, taken from the catalogue. Money in cents.
///
/// @param type          LABOR for a service, PART for a stock product of a package
/// @param serviceItemId the service a LABOR line comes from, kept on the line for later reports; null on PART lines
/// @param productId     the stock product of a PART line, which the work order reserves; null on LABOR lines
/// @param quantity      1 for a service (a package's "× n" is in the description); the units of a product, which is
///                      also what the work order reserves
public record CatalogueLine(CatalogueLineType type, @Nullable UUID serviceItemId, @Nullable UUID productId,
        String description, BigDecimal quantity, long unitPriceCents, BigDecimal vatRate) {

    public CatalogueLine {
        boolean labor = type == CatalogueLineType.LABOR;
        if (labor != (serviceItemId != null) || labor == (productId != null)) {
            throw new IllegalArgumentException("A LABOR line names a service, a PART line a product");
        }
    }

    /// A LABOR line for a service.
    public CatalogueLine(UUID serviceItemId, String description, BigDecimal quantity, long unitPriceCents,
            BigDecimal vatRate) {
        this(CatalogueLineType.LABOR, serviceItemId, null, description, quantity, unitPriceCents, vatRate);
    }

    /// A PART line for a stock product.
    public static CatalogueLine part(UUID productId, String description, BigDecimal quantity, long unitPriceCents,
            BigDecimal vatRate) {
        return new CatalogueLine(CatalogueLineType.PART, null, productId, description, quantity, unitPriceCents,
                vatRate);
    }
}
