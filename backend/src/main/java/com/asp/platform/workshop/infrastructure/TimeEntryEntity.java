package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.workshop.domain.TimeEntry;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "work_time_entry")
class TimeEntryEntity implements DomainEntity<TimeEntry> {

    @Id
    private UUID id;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Column(name = "line_id")
    private @Nullable UUID lineId;

    @Column(name = "mechanic_id", nullable = false, updatable = false)
    private UUID mechanicId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private @Nullable Instant stoppedAt;

    @Column(name = "auto_stopped", nullable = false)
    private boolean autoStopped;

    @Column(name = "edited_by")
    private @Nullable UUID editedBy;

    @Column(name = "edited_at")
    private @Nullable Instant editedAt;

    @Column(name = "edit_reason")
    private @Nullable String editReason;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected TimeEntryEntity() {
    }

    TimeEntryEntity(TimeEntry entry) {
        this.id = entry.id();
        this.workOrderId = entry.workOrderId();
        this.mechanicId = entry.mechanicId();
        apply(entry);
    }

    @Override
    public void apply(TimeEntry entry) {
        this.lineId = entry.lineId();
        this.startedAt = entry.startedAt();
        this.stoppedAt = entry.stoppedAt();
        this.autoStopped = entry.autoStopped();
        this.editedBy = entry.editedBy();
        this.editedAt = entry.editedAt();
        this.editReason = entry.editReason();
    }

    @Override
    public TimeEntry toDomain() {
        Long v = version;
        return new TimeEntry(id, workOrderId, lineId, mechanicId, startedAt, stoppedAt, autoStopped, editedBy,
                editedAt, editReason, v == null ? 0 : v);
    }
}
