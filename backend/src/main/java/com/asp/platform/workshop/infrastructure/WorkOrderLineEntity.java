package com.asp.platform.workshop.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.WorkOrderLine;

@Entity
@Table(name = "work_order_line")
class WorkOrderLineEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false, updatable = false)
    private WorkOrderEntity workOrder;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    /// The catalogue service the line was added from; null for lines typed by hand.
    @Column(name = "service_item_id", updatable = false)
    private @Nullable UUID serviceItemId;

    /// The stock product the line reserves; null when it does not come from stock.
    @Column(name = "product_id", updatable = false)
    private @Nullable UUID productId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected WorkOrderLineEntity() {
    }

    WorkOrderLineEntity(WorkOrderEntity workOrder, WorkOrderLine line) {
        this.id = line.id();
        this.workOrder = workOrder;
        this.position = line.position();
        this.type = line.type();
        this.description = line.description();
        this.quantity = line.quantity();
        this.unitPriceCents = line.unitPriceCents();
        this.discountPercent = line.discountPercent();
        this.vatRate = line.vatRate();
        this.serviceItemId = line.serviceItemId();
        this.productId = line.productId();
    }

    UUID getId() {
        return id;
    }

    WorkOrderLine toDomain() {
        return new WorkOrderLine(id, position, type, description, quantity, unitPriceCents, discountPercent,
                vatRate, serviceItemId, productId);
    }
}
