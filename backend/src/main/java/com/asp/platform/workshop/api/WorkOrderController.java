package com.asp.platform.workshop.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.WorkOrderService;

@RestController
@RequestMapping("/api/v1")
class WorkOrderController {

    private final WorkOrderService workOrders;

    WorkOrderController(WorkOrderService workOrders) {
        this.workOrders = workOrders;
    }

    /// Opens a work order when the car arrives.
    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse checkIn(CurrentUser user, @Valid @RequestBody CheckInRequest request) {
        return WorkOrderResponse.of(
                workOrders.checkIn(user, request.vehicleId(), request.toDetails(),
                        request.assignedMechanicId()), user);
    }

    @GetMapping("/work-orders/board")
    BoardResponse board(CurrentUser user) {
        return BoardResponse.of(workOrders.board(user));
    }

    /// Mechanics may open any job; one not assigned to them comes back `readOnly`.
    @GetMapping("/work-orders/{id}")
    WorkOrderResponse get(CurrentUser user, @PathVariable UUID id) {
        return WorkOrderResponse.of(workOrders.get(id), user);
    }

    @PostMapping("/work-orders/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse addLine(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody LineRequest request) {
        return WorkOrderResponse.of(workOrders.addLine(id, request.toNewLine()), user);
    }

    /// Adds a catalogue service or package at the catalogue price; a package's products become part lines that
    /// reserve stock (409 `insufficient-stock`, nothing added, when one is short).
    @PostMapping("/work-orders/{id}/lines/from-catalogue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse addCatalogueLines(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody CatalogueLineRequest request) {
        return WorkOrderResponse.of(workOrders.addCatalogueLines(id, request.serviceId(), request.packageId()), user);
    }

    /// Adds a part from stock at its sale price and reserves it; 409 `insufficient-stock` when not enough is
    /// available. Removing the line or cancelling the order gives it back; delivering the order takes it out of stock.
    @PostMapping("/work-orders/{id}/lines/from-stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse addStockLine(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody StockLineRequest request) {
        return WorkOrderResponse.of(workOrders.addStockLine(id, request.productId(), request.quantity()), user);
    }

    @DeleteMapping("/work-orders/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse removeLine(CurrentUser user, @PathVariable UUID id, @PathVariable UUID lineId) {
        return WorkOrderResponse.of(workOrders.removeLine(id, lineId), user);
    }

    /// Mechanics may move their own jobs to In progress, Awaiting parts or Ready.
    @PatchMapping("/work-orders/{id}/status")
    WorkOrderResponse move(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return WorkOrderResponse.of(workOrders.move(id, request.status(), user), user);
    }

    /// Corrects mileage, fuel, complaint and damage notes until the car is ready.
    @PutMapping("/work-orders/{id}/check-in")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse editCheckIn(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody CheckInUpdateRequest request) {
        return WorkOrderResponse.of(workOrders.editCheckIn(id, request.toDetails()), user);
    }

    /// Gives the work order to another mechanic, or to nobody. Replacing or removing a mechanic needs a reason.
    @PutMapping("/work-orders/{id}/mechanic")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse assign(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody AssignMechanicRequest request) {
        return WorkOrderResponse.of(workOrders.assign(user, id, request.mechanicId(), request.reason()), user);
    }

    /// The current mechanic and the history of changes with their reasons.
    @GetMapping("/work-orders/{id}/mechanic")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    MechanicAssignmentResponse mechanic(CurrentUser user, @PathVariable UUID id) {
        return MechanicAssignmentResponse.of(workOrders.mechanicAssignment(user.tenantId(), id));
    }

    /// Active mechanics, for choosing who works on a car.
    @GetMapping("/mechanics")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<MechanicResponse> mechanics(CurrentUser user) {
        return workOrders.mechanics(user.tenantId()).stream().map(MechanicResponse::of).toList();
    }

    /// Service history of one vehicle, newest first. Mechanics see every job, without prices.
    @GetMapping("/vehicles/{vehicleId}/work-orders")
    List<WorkOrderResponse> history(CurrentUser user, @PathVariable UUID vehicleId) {
        return workOrders.historyOf(vehicleId).stream().map(view -> WorkOrderResponse.of(view, user)).toList();
    }
}
