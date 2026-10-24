package com.asp.platform.stock;

import java.math.BigDecimal;
import java.util.UUID;

/// A product as another module puts it on a line: what it is called and what it sells for. Money in cents.
///
/// @param unit           unit of the quantity, such as "un" or "l"
/// @param salePriceCents sale price before VAT, per unit
/// @param vatRate        VAT percentage, 0 to 100
/// @param active         false when the product is no longer sold (it cannot be reserved)
public record StockItem(UUID id, String sku, String name, String unit, long salePriceCents, BigDecimal vatRate,
        boolean active) {
}
