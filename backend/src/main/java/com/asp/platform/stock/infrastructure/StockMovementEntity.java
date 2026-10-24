package com.asp.platform.stock.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

import com.asp.platform.stock.domain.MovementType;
import com.asp.platform.stock.domain.StockMovement;
import com.asp.platform.stock.domain.StockReference;

/// A ledger row: inserted once, never updated (the app's database role may not update or delete it either).
@Entity
@Table(name = "stock_movement")
@Immutable
class StockMovementEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "reference_type")
    private @Nullable String referenceType;

    @Column(name = "reference_id")
    private @Nullable UUID referenceId;

    private @Nullable String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @SuppressWarnings("NullAway.Init")
    protected StockMovementEntity() {
    }

    StockMovementEntity(StockMovement movement) {
        this.id = movement.id();
        this.productId = movement.productId();
        this.type = movement.type();
        this.quantity = movement.quantity();
        StockReference reference = movement.reference();
        this.referenceType = reference == null ? null : reference.type();
        this.referenceId = reference == null ? null : reference.id();
        this.reason = movement.reason();
        this.changedBy = movement.changedBy();
        this.changedAt = movement.changedAt();
    }

    StockMovement toDomain() {
        String refType = referenceType;
        UUID refId = referenceId;
        StockReference reference = refType == null || refId == null ? null : new StockReference(refType, refId);
        return new StockMovement(id, productId, type, quantity, reference, reason, changedBy, changedAt);
    }
}
