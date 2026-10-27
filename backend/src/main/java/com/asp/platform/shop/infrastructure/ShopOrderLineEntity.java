package com.asp.platform.shop.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.asp.platform.shop.domain.ShopOrderLine;

/// A line as the product was sold when the order was placed; never changed. `tenant_id` comes from the connection.
@Entity
@Table(name = "shop_order_line")
class ShopOrderLineEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private ShopOrderEntity order;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private String unit;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false, updatable = false)
    private long unitPriceCents;

    @Column(name = "vat_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    /// Stored for reports; always quantity times the unit price with VAT.
    @Column(name = "line_total_cents", nullable = false, updatable = false)
    private long lineTotalCents;

    @SuppressWarnings("NullAway.Init")
    protected ShopOrderLineEntity() {
    }

    ShopOrderLineEntity(ShopOrderEntity order, int position, ShopOrderLine line) {
        this.id = line.id();
        this.order = order;
        this.position = position;
        this.productId = line.productId();
        this.sku = line.sku();
        this.name = line.name();
        this.unit = line.unit();
        this.quantity = line.quantity();
        this.unitPriceCents = line.unitPriceCents();
        this.vatRate = line.vatRate();
        this.lineTotalCents = line.lineTotalCents();
    }

    ShopOrderLine toDomain() {
        return new ShopOrderLine(id, productId, sku, name, unit, quantity, unitPriceCents, vatRate);
    }
}
