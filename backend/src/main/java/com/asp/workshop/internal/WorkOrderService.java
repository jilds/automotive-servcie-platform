package com.asp.workshop.internal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.customers.VehicleDirectory;
import com.asp.customers.VehicleSummary;
import com.asp.shared.security.CurrentUser;
import com.asp.shared.security.Role;
import com.asp.workshop.WorkOrderStatus;

@Service
class WorkOrderService {

    /** Columns shown on the board; delivered and cancelled orders drop off it. */
    static final Set<WorkOrderStatus> BOARD = EnumSet.of(WorkOrderStatus.DIAGNOSTICS,
            WorkOrderStatus.AWAITING_APPROVAL, WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.READY);

    /** Moves a mechanic may make on their own work orders. */
    private static final Set<WorkOrderStatus> MECHANIC_MOVES = EnumSet.of(WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.READY);

    private final WorkOrderRepository orders;
    private final VehicleDirectory vehicles;

    WorkOrderService(WorkOrderRepository orders, VehicleDirectory vehicles) {
        this.orders = orders;
        this.vehicles = vehicles;
    }

    @Transactional
    WorkOrderDtos.WorkOrderResponse checkIn(WorkOrderDtos.CheckInRequest r) {
        VehicleSummary vehicle = vehicles.findVehicle(r.vehicleId())
                .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));
        WorkOrder wo = new WorkOrder(orders.nextNumber(), vehicle.id(), r.mileageIn(), r.fuelLevel(), r.complaint(),
                r.damageNotes());
        wo.assign(r.assignedMechanicId());
        orders.save(wo);
        if (r.mileageIn() != null) {
            vehicles.recordMileage(vehicle.id(), r.mileageIn());
        }
        return toResponse(wo, vehicle.plate());
    }

    @Transactional(readOnly = true)
    WorkOrderDtos.WorkOrderResponse get(UUID id) {
        WorkOrder wo = visible(id);
        return toResponse(wo, plateOf(wo));
    }

    @Transactional
    WorkOrderDtos.WorkOrderResponse addLine(UUID id, WorkOrderDtos.LineRequest r) {
        WorkOrder wo = order(id);
        wo.addLine(r.type(), r.description(), r.quantity(), r.unitPriceCents(), r.discountPercent(), r.vatRate());
        orders.flush();
        return toResponse(wo, plateOf(wo));
    }

    @Transactional
    WorkOrderDtos.WorkOrderResponse removeLine(UUID id, UUID lineId) {
        WorkOrder wo = order(id);
        wo.removeLine(lineId);
        return toResponse(wo, plateOf(wo));
    }

    @Transactional
    WorkOrderDtos.WorkOrderResponse move(UUID id, WorkOrderStatus target) {
        WorkOrder wo = visible(id);
        if (CurrentUser.role() == Role.MECHANIC && !MECHANIC_MOVES.contains(target)) {
            throw new AccessDeniedException("Mechanics cannot move a work order to " + target);
        }
        wo.moveTo(target);
        return toResponse(wo, plateOf(wo));
    }

    @Transactional
    WorkOrderDtos.WorkOrderResponse assign(UUID id, UUID mechanicId) {
        WorkOrder wo = order(id);
        wo.assign(mechanicId);
        return toResponse(wo, plateOf(wo));
    }

    @Transactional(readOnly = true)
    WorkOrderDtos.BoardResponse board() {
        List<WorkOrder> active = CurrentUser.role() == Role.MECHANIC
                ? orders.findByAssignedMechanicIdAndStatusInOrderByStatusChangedAt(CurrentUser.id(), BOARD)
                : orders.findByStatusInOrderByStatusChangedAt(BOARD);
        Map<WorkOrderStatus, List<WorkOrderDtos.BoardCard>> columns = new LinkedHashMap<>();
        BOARD.forEach(s -> columns.put(s, new ArrayList<>()));
        for (WorkOrder wo : active) {
            VehicleSummary v = vehicles.findVehicle(wo.getVehicleId()).orElse(null);
            String label = v == null ? "" : String.join(" ", nz(v.make()), nz(v.model())).trim();
            columns.get(wo.getStatus()).add(new WorkOrderDtos.BoardCard(wo.getId(), wo.getNumber(),
                    v == null ? null : v.plate(), label, wo.getAssignedMechanicId(), wo.getStatusChangedAt()));
        }
        return new WorkOrderDtos.BoardResponse(columns);
    }

    @Transactional(readOnly = true)
    List<WorkOrderDtos.WorkOrderResponse> historyOf(UUID vehicleId) {
        String plate = vehicles.findVehicle(vehicleId).map(VehicleSummary::plate).orElse(null);
        return orders.findByVehicleIdOrderByNumberDesc(vehicleId).stream().map(wo -> toResponse(wo, plate)).toList();
    }

    private WorkOrder order(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("Work order not found"));
    }

    /** Mechanics only see work orders assigned to them. */
    private WorkOrder visible(UUID id) {
        WorkOrder wo = order(id);
        if (CurrentUser.role() == Role.MECHANIC && !CurrentUser.id().equals(wo.getAssignedMechanicId())) {
            throw new NoSuchElementException("Work order not found");
        }
        return wo;
    }

    private String plateOf(WorkOrder wo) {
        return vehicles.findVehicle(wo.getVehicleId()).map(VehicleSummary::plate).orElse(null);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private WorkOrderDtos.WorkOrderResponse toResponse(WorkOrder wo, String plate) {
        boolean prices = CurrentUser.canSeePrices();
        List<WorkOrderDtos.LineResponse> lines = wo.getLines().stream()
                .map(l -> new WorkOrderDtos.LineResponse(l.getId(), l.getPosition(), l.getType(), l.getDescription(),
                        l.getQuantity(), prices ? l.getUnitPriceCents() : null,
                        prices ? l.getDiscountPercent() : null, prices ? l.getVatRate() : null,
                        prices ? l.netCents() : null))
                .toList();
        Long net = prices ? wo.netCents() : null;
        Long vat = prices ? wo.vatCents() : null;
        return new WorkOrderDtos.WorkOrderResponse(wo.getId(), wo.getNumber(), wo.getVehicleId(), plate,
                wo.getStatus(), List.copyOf(wo.getStatus().next()), wo.getMileageIn(), wo.getFuelLevel(),
                wo.getComplaint(), wo.getDamageNotes(), wo.getAssignedMechanicId(), wo.getStatusChangedAt(), lines,
                net, vat, prices ? net + vat : null);
    }
}
