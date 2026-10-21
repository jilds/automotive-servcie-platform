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
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.customer.VehicleSummary;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.domain.CheckInDetails;
import com.asp.platform.workshop.domain.InvalidMechanicException;
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

    /// Request fields that carry the mechanic id, reported back when the mechanic is not valid.
    private static final String CHECK_IN_MECHANIC_FIELD = "assignedMechanicId";
    private static final String ASSIGN_MECHANIC_FIELD = "mechanicId";

    private final WorkOrderRepository orders;
    private final CustomerManagement customers;
    private final WorkshopDirectory directory;
    private final Clock clock;

    WorkOrderService(WorkOrderRepository orders, CustomerManagement customers, WorkshopDirectory directory,
            Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.directory = directory;
        this.clock = clock;
    }

    /// Opens a work order when the car arrives and records the odometer reading on the vehicle.
    /// A mechanic, when given, must be an active mechanic of the workshop.
    @Transactional
    public WorkOrderView checkIn(UUID tenantId, UUID vehicleId, CheckInDetails details, @Nullable UUID mechanicId) {
        requireMechanic(tenantId, mechanicId, CHECK_IN_MECHANIC_FIELD);
        VehicleSummary vehicle = customers.findVehicle(vehicleId)
                .orElseThrow(UnknownVehicleException::new);
        WorkOrder order = orders.save(WorkOrder.open(UUID.randomUUID(), orders.nextNumber(), vehicle.id(), details,
                mechanicId, clock.instant()));
        if (details.mileageIn() != null) {
            customers.recordMileage(vehicle.id(), details.mileageIn());
        }
        return new WorkOrderView(order, VehiclePlate.of(vehicle));
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

    /// Corrects the check-in data while the order is still editable. A changed odometer reading is also
    /// recorded on the vehicle, which keeps the highest reading it has seen.
    @Transactional
    public WorkOrderView editCheckIn(UUID id, CheckInDetails corrected) {
        WorkOrder order = order(id);
        @Nullable Integer previousMileage = order.checkIn().mileageIn();
        order.editCheckIn(corrected);
        WorkOrder saved = orders.save(order);
        @Nullable Integer mileage = corrected.mileageIn();
        if (mileage != null && !mileage.equals(previousMileage)) {
            customers.recordMileage(saved.vehicleId(), mileage);
        }
        return view(saved);
    }

    /// Assigns the work order to an active mechanic of the workshop; a null mechanic unassigns it.
    @Transactional
    public WorkOrderView assign(UUID tenantId, UUID id, @Nullable UUID mechanicId) {
        requireMechanic(tenantId, mechanicId, ASSIGN_MECHANIC_FIELD);
        WorkOrder order = order(id);
        order.assign(mechanicId);
        return view(orders.save(order));
    }

    /// Mechanics see only the jobs assigned to them. The vehicles of all cards are read in one query.
    @Transactional(readOnly = true)
    public Board board(CurrentUser user) {
        Set<WorkOrderStatus> columns = WorkOrderStatus.boardColumns();
        List<WorkOrder> active = user.isMechanic()
                ? orders.findByMechanicAndStatusIn(user.userId(), columns)
                : orders.findByStatusIn(columns);
        Map<UUID, VehicleSummary> vehicles = active.isEmpty() ? Map.of()
                : customers.findVehicles(active.stream().map(WorkOrder::vehicleId).collect(Collectors.toSet()));
        Map<WorkOrderStatus, List<BoardCard>> board = new LinkedHashMap<>();
        columns.forEach(status -> board.put(status, new ArrayList<>()));
        for (WorkOrder order : active) {
            Optional<VehicleSummary> vehicle = Optional.ofNullable(vehicles.get(order.vehicleId()));
            board.get(order.status()).add(new BoardCard(order.id(), order.number(),
                    vehicle.map(VehiclePlate::of).orElse(null), vehicle.map(VehicleSummary::label).orElse(""),
                    order.assignedMechanicId(), order.statusChangedAt()));
        }
        return new Board(board);
    }

    /// Service history of one vehicle, newest first.
    @Transactional(readOnly = true)
    public List<WorkOrderView> historyOf(UUID vehicleId) {
        VehiclePlate plate = plateOf(vehicleId);
        return orders.findByVehicle(vehicleId).stream().map(order -> new WorkOrderView(order, plate)).toList();
    }

    /// The user table has no row-level security, so the workshop is checked explicitly.
    private void requireMechanic(UUID tenantId, @Nullable UUID mechanicId, String field) {
        if (mechanicId != null && !directory.isActiveMechanic(tenantId, mechanicId)) {
            throw new InvalidMechanicException(field);
        }
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

    private @Nullable VehiclePlate plateOf(UUID vehicleId) {
        return customers.findVehicle(vehicleId).map(VehiclePlate::of).orElse(null);
    }
}
