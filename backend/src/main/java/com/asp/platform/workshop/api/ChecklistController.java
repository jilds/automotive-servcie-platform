package com.asp.platform.workshop.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.ChecklistService;
import com.asp.platform.workshop.application.StartedChecklist;
import com.asp.platform.workshop.domain.ChecklistKind;

/// Checklist templates (owner) and the checklists of work orders (everyone reads; the assigned mechanic, the owner and
/// the front desk fill them in). No prices and no customer data in any answer.
@RestController
@RequestMapping("/api/v1")
class ChecklistController {

    private final ChecklistService checklists;

    ChecklistController(ChecklistService checklists) {
        this.checklists = checklists;
    }

    @GetMapping("/checklist-templates")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<ChecklistTemplateResponse> templates(CurrentUser user) {
        return checklists.templates(user.tenantId()).stream().map(ChecklistTemplateResponse::of).toList();
    }

    /// Replaces all items of the template. A `kind` other than CHECK_IN / DELIVERY is a 400 `validation`.
    @PutMapping("/checklist-templates/{kind}")
    @PreAuthorize("hasRole('OWNER')")
    ChecklistTemplateResponse saveTemplate(CurrentUser user, @PathVariable ChecklistKind kind,
            @Valid @RequestBody ChecklistTemplateRequest request) {
        return ChecklistTemplateResponse.of(
                checklists.saveTemplate(user.tenantId(), kind, request.toItems(), request.version()));
    }

    @GetMapping("/work-orders/{id}/checklists")
    WorkOrderChecklistsResponse forOrder(CurrentUser user, @PathVariable UUID id) {
        return WorkOrderChecklistsResponse.of(checklists.forOrder(user, id));
    }

    /// 201 when the checklist is started now (a copy of the template); 200 with the existing one otherwise.
    @PostMapping("/work-orders/{id}/checklists/{kind}")
    ResponseEntity<ChecklistResponse> start(CurrentUser user, @PathVariable UUID id,
            @PathVariable ChecklistKind kind) {
        StartedChecklist started = checklists.start(user, id, kind);
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ChecklistResponse.of(started.checklist()));
    }

    @PutMapping("/work-orders/{id}/checklists/{kind}/items/{itemId}")
    ChecklistResponse answer(CurrentUser user, @PathVariable UUID id, @PathVariable ChecklistKind kind,
            @PathVariable UUID itemId, @Valid @RequestBody ChecklistAnswerRequest request) {
        return ChecklistResponse.of(checklists.answer(user, id, kind, itemId, request.result(), request.note()));
    }
}
