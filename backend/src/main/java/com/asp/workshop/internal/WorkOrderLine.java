package com.asp.workshop.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.asp.shared.tenancy.TenantScopedEntity;

/** A labor or part line. Money is kept in integer cents. */
@Entity
@Table(name = "work_order_line")
class WorkOrderLine extends TenantScopedEntity {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

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

    protected WorkOrderLine() {
    }

    WorkOrderLine(WorkOrder workOrder, int position, LineType type, String description, BigDecimal quantity,
            long unitPriceCents, BigDecimal discountPercent, BigDecimal vatRate) {
        this.workOrder = workOrder;
        this.position = position;
        this.type = type;
        this.description = description;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
        this.discountPercent = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        this.vatRate = vatRate;
    }

    long netCents() {
        BigDecimal gross = quantity.multiply(BigDecimal.valueOf(unitPriceCents));
        BigDecimal factor = HUNDRED.subtract(discountPercent).divide(HUNDRED);
        return gross.multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    long vatCents() {
        return BigDecimal.valueOf(netCents()).multiply(vatRate).divide(HUNDRED).setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    UUID getId() { return id; }
    int getPosition() { return position; }
    LineType getType() { return type; }
    String getDescription() { return description; }
    BigDecimal getQuantity() { return quantity; }
    long getUnitPriceCents() { return unitPriceCents; }
    BigDecimal getDiscountPercent() { return discountPercent; }
    BigDecimal getVatRate() { return vatRate; }
}
