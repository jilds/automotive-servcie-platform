package com.asp.workshop.internal;

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

@RestController
@RequestMapping("/api/v1")
class WorkOrderController {

    private final WorkOrderService service;

    WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    /** Opens a work order when the car arrives. */
    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderDtos.WorkOrderResponse checkIn(@Valid @RequestBody WorkOrderDtos.CheckInRequest req) {
        return service.checkIn(req);
    }

    @GetMapping("/work-orders/board")
    WorkOrderDtos.BoardResponse board() {
        return service.board();
    }

    @GetMapping("/work-orders/{id}")
    WorkOrderDtos.WorkOrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/work-orders/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderDtos.WorkOrderResponse addLine(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.LineRequest req) {
        return service.addLine(id, req);
    }

    @DeleteMapping("/work-orders/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderDtos.WorkOrderResponse removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return service.removeLine(id, lineId);
    }

    @PatchMapping("/work-orders/{id}/status")
    WorkOrderDtos.WorkOrderResponse move(@PathVariable UUID id, @Valid @RequestBody WorkOrderDtos.StatusRequest req) {
        return service.move(id, req.status());
    }

    @PutMapping("/work-orders/{id}/mechanic")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    WorkOrderDtos.WorkOrderResponse assign(@PathVariable UUID id, @RequestBody WorkOrderDtos.AssignRequest req) {
        return service.assign(id, req.mechanicId());
    }

    /** Service history of one vehicle, newest first. */
    @GetMapping("/vehicles/{vehicleId}/work-orders")
    List<WorkOrderDtos.WorkOrderResponse> history(@PathVariable UUID vehicleId) {
        return service.historyOf(vehicleId);
    }
}
