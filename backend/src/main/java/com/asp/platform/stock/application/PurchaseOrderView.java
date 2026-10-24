package com.asp.platform.stock.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.asp.platform.stock.domain.PurchaseOrder;

/// A purchase order with the SKU, name and unit of each product on it.
public record PurchaseOrderView(PurchaseOrder order, List<Line> lines) {

    /// @param totalCents quantity times unit cost, rounded half up
    public record Line(UUID productId, String sku, String name, String unit, BigDecimal quantity, long unitCostCents,
            long totalCents) {
    }
}
