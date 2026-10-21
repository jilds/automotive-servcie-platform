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
                workOrders.checkIn(user.tenantId(), request.vehicleId(), request.toDetails(),
                        request.assignedMechanicId()), user);
    }

    @GetMapping("/work-orders/board")
    BoardResponse board(CurrentUser user) {
        return BoardResponse.of(workOrders.board(user));
    }

    @GetMapping("/work-orders/{id}")
    WorkOrderResponse get(CurrentUser user, @PathVariable UUID id) {
        return WorkOrderResponse.of(workOrders.get(id, user), user);
    }

    @PostMapping("/work-orders/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse addLine(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody LineRequest request) {
        return WorkOrderResponse.of(workOrders.addLine(id, request.toNewLine()), user);
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

    @PutMapping("/work-orders/{id}/mechanic")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderResponse assign(CurrentUser user, @PathVariable UUID id, @RequestBody AssignMechanicRequest request) {
        return WorkOrderResponse.of(workOrders.assign(user.tenantId(), id, request.mechanicId()), user);
    }

    /// Service history of one vehicle, newest first.
    @GetMapping("/vehicles/{vehicleId}/work-orders")
    List<WorkOrderResponse> history(CurrentUser user, @PathVariable UUID vehicleId) {
        return workOrders.historyOf(vehicleId).stream().map(view -> WorkOrderResponse.of(view, user)).toList();
    }
}
