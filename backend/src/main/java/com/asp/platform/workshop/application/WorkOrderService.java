package com.asp.platform.workshop.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.customer.VehicleSummary;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.domain.CheckInDetails;
import com.asp.platform.workshop.domain.NewLine;
import com.asp.platform.workshop.domain.UnknownVehicleException;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderNotFoundException;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

@Service
public class WorkOrderService {

    /// Moves a mechanic may make on their own work orders.
    private static final Set<WorkOrderStatus> MECHANIC_MOVES = EnumSet.of(WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.READY);

    private final WorkOrderRepository orders;
    private final CustomerManagement customers;
    private final Clock clock;

    WorkOrderService(WorkOrderRepository orders, CustomerManagement customers, Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.clock = clock;
    }

    /// Opens a work order when the car arrives and records the odometer reading on the vehicle.
    @Transactional
    public WorkOrderView checkIn(UUID vehicleId, CheckInDetails details, @Nullable UUID mechanicId) {
        VehicleSummary vehicle = customers.findVehicle(vehicleId)
                .orElseThrow(UnknownVehicleException::new);
        WorkOrder order = orders.save(WorkOrder.open(UUID.randomUUID(), orders.nextNumber(), vehicle.id(), details,
                mechanicId, clock.instant()));
        if (details.mileageIn() != null) {
            customers.recordMileage(vehicle.id(), details.mileageIn());
        }
        return new WorkOrderView(order, vehicle.plate());
    }

    @Transactional(readOnly = true)
    public WorkOrderView get(UUID id, CurrentUser user) {
        return view(visible(id, user));
    }

    @Transactional
    public WorkOrderView addLine(UUID id, NewLine line) {
        WorkOrder order = order(id);
        order.addLine(UUID.randomUUID(), line);
        return view(orders.save(order));
    }

    @Transactional
    public WorkOrderView removeLine(UUID id, UUID lineId) {
        WorkOrder order = order(id);
        order.removeLine(lineId);
        return view(orders.save(order));
    }

    @Transactional
    public WorkOrderView move(UUID id, WorkOrderStatus target, CurrentUser user) {
        WorkOrder order = visible(id, user);
        if (user.isMechanic() && !MECHANIC_MOVES.contains(target)) {
            throw new AccessDeniedException("Mechanics cannot move a work order to " + target);
        }
        order.moveTo(target, clock.instant());
        return view(orders.save(order));
    }

    @Transactional
    public WorkOrderView assign(UUID id, @Nullable UUID mechanicId) {
        WorkOrder order = order(id);
        order.assign(mechanicId);
        return view(orders.save(order));
    }

    /// Mechanics see only the jobs assigned to them.
    @Transactional(readOnly = true)
    public Board board(CurrentUser user) {
        Set<WorkOrderStatus> columns = WorkOrderStatus.boardColumns();
        List<WorkOrder> active = user.isMechanic()
                ? orders.findByMechanicAndStatusIn(user.userId(), columns)
                : orders.findByStatusIn(columns);
        Map<WorkOrderStatus, List<BoardCard>> board = new LinkedHashMap<>();
        columns.forEach(status -> board.put(status, new ArrayList<>()));
        for (WorkOrder order : active) {
            Optional<VehicleSummary> vehicle = customers.findVehicle(order.vehicleId());
            board.get(order.status()).add(new BoardCard(order.id(), order.number(),
                    vehicle.map(VehicleSummary::plate).orElse(null), vehicle.map(VehicleSummary::label).orElse(""),
                    order.assignedMechanicId(), order.statusChangedAt()));
        }
        return new Board(board);
    }

    /// Service history of one vehicle, newest first.
    @Transactional(readOnly = true)
    public List<WorkOrderView> historyOf(UUID vehicleId) {
        String plate = plateOf(vehicleId);
        return orders.findByVehicle(vehicleId).stream().map(order -> new WorkOrderView(order, plate)).toList();
    }

    private WorkOrder order(UUID id) {
        return orders.findById(id).orElseThrow(WorkOrderNotFoundException::new);
    }

    /// A mechanic asking for someone else's job gets "not found", so ids of other jobs reveal nothing.
    private WorkOrder visible(UUID id, CurrentUser user) {
        WorkOrder order = order(id);
        if (user.isMechanic() && !order.isAssignedTo(user.userId())) {
            throw new WorkOrderNotFoundException();
        }
        return order;
    }

    private WorkOrderView view(WorkOrder order) {
        return new WorkOrderView(order, plateOf(order.vehicleId()));
    }

    private @Nullable String plateOf(UUID vehicleId) {
        return customers.findVehicle(vehicleId).map(VehicleSummary::plate).orElse(null);
    }
}
