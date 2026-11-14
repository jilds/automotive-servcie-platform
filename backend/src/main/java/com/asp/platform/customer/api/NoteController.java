package com.asp.platform.customer.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.customer.application.NoteService;
import com.asp.platform.shared.CurrentUser;

/// Vehicle observations are open to every role (the car's history; not for customer personal data). Customer notes
/// are for the owner and the front desk only, like every customer endpoint.
@RestController
@RequestMapping("/api/v1")
class NoteController {

    private final NoteService notes;

    NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping("/vehicles/{id}/observations")
    List<NoteResponse> vehicleObservations(CurrentUser user, @PathVariable UUID id) {
        return notes.vehicleObservations(user, id).stream().map(NoteResponse::of).toList();
    }

    @PostMapping("/vehicles/{id}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    NoteResponse addVehicleObservation(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        return NoteResponse.of(notes.addVehicleObservation(user, id, request.body()));
    }

    @GetMapping("/customers/{id}/notes")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<NoteResponse> customerNotes(CurrentUser user, @PathVariable UUID id) {
        return notes.customerNotes(user, id).stream().map(NoteResponse::of).toList();
    }

    @PostMapping("/customers/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    NoteResponse addCustomerNote(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return NoteResponse.of(notes.addCustomerNote(user, id, request.body()));
    }
}
