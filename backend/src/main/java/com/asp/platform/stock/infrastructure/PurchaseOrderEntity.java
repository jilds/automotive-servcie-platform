package com.asp.platform.stock.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.PurchaseOrder;
import com.asp.platform.stock.domain.PurchaseOrderDetails;
import com.asp.platform.stock.domain.PurchaseOrderLine;
import com.asp.platform.stock.domain.PurchaseOrderStatus;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "purchase_order")
class PurchaseOrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String supplier;

    private @Nullable String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status;

    @Column(name = "ordered_at")
    private @Nullable Instant orderedAt;

    @Column(name = "received_at")
    private @Nullable Instant receivedAt;

    /// Optimistic lock: also stops two people receiving the same order at once.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PurchaseOrderLineEntity> lines = new ArrayList<>();

    @SuppressWarnings("NullAway.Init")
    protected PurchaseOrderEntity() {
    }

    PurchaseOrderEntity(PurchaseOrder order) {
        this.id = order.id();
        this.number = order.number();
        apply(order);
    }

    /// Copies the changeable state, adding and removing lines to match (a line keeps its id while it stays).
    void apply(PurchaseOrder order) {
        PurchaseOrderDetails d = order.details();
        this.supplier = d.supplier();
        this.notes = d.notes();
        this.status = order.status();
        this.orderedAt = order.orderedAt();
        this.receivedAt = order.receivedAt();
        Set<UUID> kept = d.lines().stream().map(PurchaseOrderLine::id).collect(Collectors.toSet());
        lines.removeIf(line -> !kept.contains(line.getId()));
        Set<UUID> existing = lines.stream().map(PurchaseOrderLineEntity::getId).collect(Collectors.toSet());
        for (int i = 0; i < d.lines().size(); i++) {
            PurchaseOrderLine line = d.lines().get(i);
            if (!existing.contains(line.id())) {
                lines.add(new PurchaseOrderLineEntity(this, i + 1, line));
            }
        }
    }

    PurchaseOrder toDomain() {
        Long v = version;
        return new PurchaseOrder(id, number,
                new PurchaseOrderDetails(supplier, notes,
                        lines.stream().map(PurchaseOrderLineEntity::toDomain).toList()),
                status, orderedAt, receivedAt, v == null ? 0 : v);
    }
}
