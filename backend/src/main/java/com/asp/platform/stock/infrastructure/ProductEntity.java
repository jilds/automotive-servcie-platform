package com.asp.platform.stock.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.Product;
import com.asp.platform.stock.domain.ProductDetails;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
///
/// Only changed columns are written ([DynamicUpdate]), and a change of `on_hand` alone does not raise the version
/// ([OptimisticLock] excluded): stock movements happen under a row lock, and must neither overwrite an edit of the
/// details nor make an open edit dialog stale.
@Entity
@Table(name = "product")
@DynamicUpdate
class ProductEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private @Nullable String brand;

    @Column(nullable = false)
    private String unit;

    @Column(name = "cost_cents", nullable = false)
    private long costCents;

    @Column(name = "sale_price_cents", nullable = false)
    private long salePriceCents;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "min_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal minStock;

    private @Nullable String bin;

    @OptimisticLock(excluded = true)
    @Column(name = "on_hand", nullable = false, precision = 12, scale = 3)
    private BigDecimal onHand;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "listed_online", nullable = false)
    private boolean listedOnline;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @OptimisticLock(excluded = true)
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected ProductEntity() {
    }

    ProductEntity(Product product) {
        this.id = product.id();
        apply(product);
    }

    void apply(Product product) {
        ProductDetails d = product.details();
        this.sku = d.sku();
        this.name = d.name();
        this.brand = d.brand();
        this.unit = d.unit();
        this.costCents = d.costCents();
        this.salePriceCents = d.salePriceCents();
        this.vatRate = d.vatRate();
        this.minStock = d.minStock();
        this.bin = d.bin();
        this.active = d.active();
        this.listedOnline = d.listedOnline();
        // Compared by value: 2.000 and 2 are the same stock, and must not count as a change.
        if (onHand == null || onHand.compareTo(product.onHand()) != 0) {
            this.onHand = product.onHand();
        }
    }

    Product toDomain() {
        Long v = version;
        return new Product(id, new ProductDetails(sku, name, brand, unit, costCents, salePriceCents, vatRate,
                minStock, bin, active, listedOnline), onHand, v == null ? 0 : v);
    }
}
