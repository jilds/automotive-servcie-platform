package com.asp.platform.stock.domain;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// What the workshop orders from a supplier.
///
/// @param supplier the supplier's name, as free text
/// @param lines    at least one product
public record PurchaseOrderDetails(String supplier, @Nullable String notes, List<PurchaseOrderLine> lines) {

    public static final int MAX_SUPPLIER = 120;
    public static final int MAX_NOTES = 1000;
    public static final int MAX_LINES = 100;

    public PurchaseOrderDetails {
        supplier = Text.required(supplier, "Supplier", MAX_SUPPLIER);
        notes = Text.optional(notes, "Notes", MAX_NOTES);
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("A purchase order has 1 to " + MAX_LINES + " lines");
        }
        lines = List.copyOf(lines);
    }
}
