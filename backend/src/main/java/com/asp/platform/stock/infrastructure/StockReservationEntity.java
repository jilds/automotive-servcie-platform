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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.domain.Reservation;
import com.asp.platform.stock.domain.ReservationStatus;

/// Only the status changes after the row is written.
@Entity
@Table(name = "stock_reservation")
class StockReservationEntity implements DomainEntity<Reservation> {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReservationSource source;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "source_line_id", nullable = false, updatable = false)
    private UUID sourceLineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected StockReservationEntity() {
    }

    StockReservationEntity(Reservation reservation) {
        this.id = reservation.id();
        this.productId = reservation.productId();
        this.quantity = reservation.quantity();
        this.source = reservation.source();
        this.sourceId = reservation.sourceId();
        this.sourceLineId = reservation.sourceLineId();
        this.status = reservation.status();
    }

    @Override
    public void apply(Reservation reservation) {
        this.status = reservation.status();
    }

    @Override
    public Reservation toDomain() {
        return new Reservation(id, productId, quantity, source, sourceId, sourceLineId, status);
    }
}
