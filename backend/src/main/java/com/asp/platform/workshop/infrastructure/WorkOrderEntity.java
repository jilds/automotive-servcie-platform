package com.asp.platform.workshop.infrastructure;

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

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.workshop.domain.CheckInDetails;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderLine;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "work_order")
class WorkOrderEntity implements DomainEntity<WorkOrder> {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status;

    @Column(name = "mileage_in")
    private @Nullable Integer mileageIn;

    @Column(name = "fuel_level")
    private @Nullable Integer fuelLevel;

    private @Nullable String complaint;

    @Column(name = "damage_notes")
    private @Nullable String damageNotes;

    @Column(name = "assigned_mechanic_id")
    private @Nullable UUID assignedMechanicId;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    /// Optimistic locking: two people editing the same order cannot silently overwrite each other.
    @Version
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<WorkOrderLineEntity> lines = new ArrayList<>();

    @SuppressWarnings("NullAway.Init")
    protected WorkOrderEntity() {
    }

    WorkOrderEntity(WorkOrder order) {
        this.id = order.id();
        this.number = order.number();
        this.vehicleId = order.vehicleId();
        apply(order);
    }

    /// Copies the changeable state of the domain object, adding and removing lines to match.
    @Override
    public void apply(WorkOrder order) {
        CheckInDetails checkIn = order.checkIn();
        this.mileageIn = checkIn.mileageIn();
        this.fuelLevel = checkIn.fuelLevel();
        this.complaint = checkIn.complaint();
        this.damageNotes = checkIn.damageNotes();
        this.status = order.status();
        this.assignedMechanicId = order.assignedMechanicId();
        this.statusChangedAt = order.statusChangedAt();
        Set<UUID> kept = order.lines().stream().map(WorkOrderLine::id).collect(Collectors.toSet());
        lines.removeIf(line -> !kept.contains(line.getId()));
        Set<UUID> existing = lines.stream().map(WorkOrderLineEntity::getId).collect(Collectors.toSet());
        order.lines().stream()
                .filter(line -> !existing.contains(line.id()))
                .forEach(line -> lines.add(new WorkOrderLineEntity(this, line)));
    }

    @Override
    public WorkOrder toDomain() {
        return new WorkOrder(id, number, vehicleId, new CheckInDetails(mileageIn, fuelLevel, complaint, damageNotes),
                status, assignedMechanicId, statusChangedAt,
                lines.stream().map(WorkOrderLineEntity::toDomain).toList());
    }
}
