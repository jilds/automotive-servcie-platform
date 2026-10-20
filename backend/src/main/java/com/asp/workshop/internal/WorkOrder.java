package com.asp.workshop.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.asp.shared.tenancy.TenantScopedEntity;
import com.asp.shared.web.ConflictException;
import com.asp.workshop.WorkOrderStatus;

@Entity
@Table(name = "work_order")
class WorkOrder extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.DIAGNOSTICS;

    @Column(name = "mileage_in")
    private Integer mileageIn;

    /** Fuel level at check-in, from 0 (empty) to 8 (full) eighths. */
    @Column(name = "fuel_level")
    private Integer fuelLevel;

    private String complaint;

    @Column(name = "damage_notes")
    private String damageNotes;

    @Column(name = "assigned_mechanic_id")
    private UUID assignedMechanicId;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<WorkOrderLine> lines = new ArrayList<>();

    protected WorkOrder() {
    }

    WorkOrder(int number, UUID vehicleId, Integer mileageIn, Integer fuelLevel, String complaint, String damageNotes) {
        this.number = number;
        this.vehicleId = vehicleId;
        this.mileageIn = mileageIn;
        this.fuelLevel = fuelLevel;
        this.complaint = complaint;
        this.damageNotes = damageNotes;
    }

    void moveTo(WorkOrderStatus target) {
        if (!status.canMoveTo(target)) {
            throw new ConflictException("Cannot move work order from " + status + " to " + target);
        }
        status = target;
        statusChangedAt = Instant.now();
    }

    WorkOrderLine addLine(LineType type, String description, java.math.BigDecimal quantity, long unitPriceCents,
            java.math.BigDecimal discountPercent, java.math.BigDecimal vatRate) {
        requireEditable();
        WorkOrderLine line = new WorkOrderLine(this, lines.size() + 1, type, description, quantity, unitPriceCents,
                discountPercent, vatRate);
        lines.add(line);
        return line;
    }

    void removeLine(UUID lineId) {
        requireEditable();
        if (!lines.removeIf(l -> lineId.equals(l.getId()))) {
            throw new java.util.NoSuchElementException("Line not found");
        }
    }

    void assign(UUID mechanicId) {
        this.assignedMechanicId = mechanicId;
    }

    private void requireEditable() {
        if (!status.isEditable()) {
            throw new ConflictException("Work order is " + status + " and can no longer be changed");
        }
    }

    long netCents() {
        return lines.stream().mapToLong(WorkOrderLine::netCents).sum();
    }

    long vatCents() {
        return lines.stream().mapToLong(WorkOrderLine::vatCents).sum();
    }

    UUID getId() { return id; }
    int getNumber() { return number; }
    UUID getVehicleId() { return vehicleId; }
    WorkOrderStatus getStatus() { return status; }
    Integer getMileageIn() { return mileageIn; }
    Integer getFuelLevel() { return fuelLevel; }
    String getComplaint() { return complaint; }
    String getDamageNotes() { return damageNotes; }
    UUID getAssignedMechanicId() { return assignedMechanicId; }
    Instant getStatusChangedAt() { return statusChangedAt; }
    List<WorkOrderLine> getLines() { return lines; }
}
