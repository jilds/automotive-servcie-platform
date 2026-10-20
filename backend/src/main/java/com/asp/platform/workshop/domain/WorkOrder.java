package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One visit of a vehicle to the workshop, from check-in to delivery.
public final class WorkOrder {

    private final UUID id;
    private final int number;
    private final UUID vehicleId;
    private final CheckInDetails checkIn;
    private WorkOrderStatus status;
    private @Nullable UUID assignedMechanicId;
    private Instant statusChangedAt;
    private final List<WorkOrderLine> lines;

    public WorkOrder(UUID id, int number, UUID vehicleId, CheckInDetails checkIn, WorkOrderStatus status,
            @Nullable UUID assignedMechanicId, Instant statusChangedAt, List<WorkOrderLine> lines) {
        this.id = id;
        this.number = number;
        this.vehicleId = vehicleId;
        this.checkIn = checkIn;
        this.status = status;
        this.assignedMechanicId = assignedMechanicId;
        this.statusChangedAt = statusChangedAt;
        this.lines = new ArrayList<>(lines);
    }

    /// Opens a work order in diagnostics when the car arrives.
    public static WorkOrder open(UUID id, int number, UUID vehicleId, CheckInDetails checkIn,
            @Nullable UUID assignedMechanicId, Instant now) {
        return new WorkOrder(id, number, vehicleId, checkIn, WorkOrderStatus.DIAGNOSTICS, assignedMechanicId, now,
                List.of());
    }

    public void moveTo(WorkOrderStatus target, Instant now) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStatusChangeException(status, target);
        }
        status = target;
        statusChangedAt = now;
    }

    public WorkOrderLine addLine(UUID lineId, NewLine line) {
        requireEditable();
        WorkOrderLine added = WorkOrderLine.from(lineId, nextPosition(), line);
        lines.add(added);
        return added;
    }

    public void removeLine(UUID lineId) {
        requireEditable();
        if (!lines.removeIf(line -> line.id().equals(lineId))) {
            throw new LineNotFoundException();
        }
    }

    public void assign(@Nullable UUID mechanicId) {
        this.assignedMechanicId = mechanicId;
    }

    public boolean isAssignedTo(UUID mechanicId) {
        return mechanicId.equals(assignedMechanicId);
    }

    public long netCents() {
        return lines.stream().mapToLong(WorkOrderLine::netCents).sum();
    }

    public long vatCents() {
        return lines.stream().mapToLong(WorkOrderLine::vatCents).sum();
    }

    public long totalCents() {
        return netCents() + vatCents();
    }

    private int nextPosition() {
        return lines.stream().mapToInt(WorkOrderLine::position).max().orElse(0) + 1;
    }

    private void requireEditable() {
        if (!status.isEditable()) {
            throw new WorkOrderLockedException(status);
        }
    }

    public UUID id() {
        return id;
    }

    public int number() {
        return number;
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public CheckInDetails checkIn() {
        return checkIn;
    }

    public WorkOrderStatus status() {
        return status;
    }

    public @Nullable UUID assignedMechanicId() {
        return assignedMechanicId;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }

    public List<WorkOrderLine> lines() {
        return List.copyOf(lines);
    }
}
