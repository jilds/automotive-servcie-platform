package com.asp.platform.stock.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.application.PurchaseOrderView;
import com.asp.platform.stock.domain.PurchaseOrder;
import com.asp.platform.stock.domain.PurchaseOrderStatus;

/// A purchase order with its lines. Money in cents; `totalCents` is the sum of the lines (quantity × unit cost,
/// rounded half up per line). `version` goes back with the next edit of a draft.
record PurchaseOrderResponse(UUID id, int number, String supplier, @Nullable String notes, PurchaseOrderStatus status,
        @Nullable Instant orderedAt, @Nullable Instant receivedAt, long totalCents, List<Line> lines, long version) {

    record Line(UUID productId, String sku, String name, String unit, BigDecimal quantity, long unitCostCents,
            long totalCents) {
    }

    static PurchaseOrderResponse of(PurchaseOrderView view) {
        PurchaseOrder o = view.order();
        return new PurchaseOrderResponse(o.id(), o.number(), o.details().supplier(), o.details().notes(), o.status(),
                o.orderedAt(), o.receivedAt(), o.totalCents(),
                view.lines().stream()
                        .map(l -> new Line(l.productId(), l.sku(), l.name(), l.unit(), l.quantity(), l.unitCostCents(),
                                l.totalCents()))
                        .toList(),
                o.version());
    }
}
