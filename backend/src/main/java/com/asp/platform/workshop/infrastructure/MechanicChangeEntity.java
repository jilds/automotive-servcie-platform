package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.MechanicChange;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "work_order_mechanic_change")
class MechanicChangeEntity {

    @Id
    private UUID id;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "from_mechanic_id", updatable = false)
    private @Nullable UUID fromMechanicId;

    @Column(name = "to_mechanic_id", updatable = false)
    private @Nullable UUID toMechanicId;

    @Column(updatable = false)
    private @Nullable String reason;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @SuppressWarnings("NullAway.Init")
    protected MechanicChangeEntity() {
    }

    MechanicChangeEntity(MechanicChange change) {
        this.id = change.id();
        this.workOrderId = change.workOrderId();
        this.fromMechanicId = change.fromMechanicId();
        this.toMechanicId = change.toMechanicId();
        this.reason = change.reason();
        this.changedBy = change.changedBy();
        this.changedAt = change.changedAt();
    }

    MechanicChange toDomain() {
        return new MechanicChange(id, workOrderId, fromMechanicId, toMechanicId, reason, changedBy, changedAt);
    }
}
