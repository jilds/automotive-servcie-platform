package com.asp.platform.stock.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.asp.platform.stock.domain.PurchaseOrderLine;

/// A line never changes once written: an edit of a draft replaces its lines.
@Entity
@Table(name = "purchase_order_line")
class PurchaseOrderLineEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    private PurchaseOrderEntity purchaseOrder;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost_cents", nullable = false, updatable = false)
    private long unitCostCents;

    @SuppressWarnings("NullAway.Init")
    protected PurchaseOrderLineEntity() {
    }

    PurchaseOrderLineEntity(PurchaseOrderEntity purchaseOrder, int position, PurchaseOrderLine line) {
        this.id = line.id();
        this.purchaseOrder = purchaseOrder;
        this.position = position;
        this.productId = line.productId();
        this.quantity = line.quantity();
        this.unitCostCents = line.unitCostCents();
    }

    UUID getId() {
        return id;
    }

    PurchaseOrderLine toDomain() {
        return new PurchaseOrderLine(id, productId, quantity, unitCostCents);
    }
}
