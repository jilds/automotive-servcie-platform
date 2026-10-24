package com.asp.platform.stock.domain;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/// What the workshop records about a part it keeps in stock. The quantity on hand is not part of it: that only
/// changes through stock movements.
///
/// @param sku            the workshop's own code for the part, unique in the workshop whatever its case
/// @param unit           unit of the quantities, such as "un" or "l"
/// @param costCents      what the part costs the workshop, before VAT, in cents
/// @param salePriceCents what the customer pays, before VAT, in cents
/// @param vatRate        VAT percentage, 0 to 100
/// @param minStock       below this available quantity the product counts as low stock
/// @param bin            where the part is kept, such as "A-03"
/// @param active         inactive products stay on old work orders but can no longer be reserved or ordered
/// @param listedOnline   shown in the workshop's online shop when that is switched on
public record ProductDetails(String sku, String name, @Nullable String brand, String unit, long costCents,
        long salePriceCents, BigDecimal vatRate, BigDecimal minStock, @Nullable String bin, boolean active,
        boolean listedOnline) {

    public static final int MAX_SKU = 40;
    public static final int MAX_NAME = 120;
    public static final int MAX_BRAND = 60;
    public static final int MAX_UNIT = 10;
    public static final int MAX_BIN = 40;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ProductDetails {
        sku = Text.required(sku, "SKU", MAX_SKU);
        name = Text.required(name, "Name", MAX_NAME);
        brand = Text.optional(brand, "Brand", MAX_BRAND);
        unit = Text.required(unit, "Unit", MAX_UNIT);
        bin = Text.optional(bin, "Bin", MAX_BIN);
        if (costCents < 0) {
            throw new IllegalArgumentException("Cost cannot be negative");
        }
        if (salePriceCents < 0) {
            throw new IllegalArgumentException("Sale price cannot be negative");
        }
        if (vatRate.signum() < 0 || vatRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        Quantities.notNegative(minStock, "Minimum stock");
    }
}
