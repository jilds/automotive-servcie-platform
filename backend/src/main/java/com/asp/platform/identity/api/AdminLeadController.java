package com.asp.platform.identity.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.LeadService;
import com.asp.platform.identity.domain.Lead;

/// The demo requests from the landing site, for the platform admin only (like the rest of `/api/v1/admin/**`).
@RestController
@RequestMapping("/api/v1/admin/leads")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class AdminLeadController {

    private final LeadService leads;

    AdminLeadController(LeadService leads) {
        this.leads = leads;
    }

    /// Newest first, at most 500; `?status=NEW|CONTACTED|CLOSED` filters.
    @GetMapping
    List<LeadResponse> list(@RequestParam(required = false) Lead.@Nullable Status status) {
        return leads.list(status).stream().map(LeadResponse::of).toList();
    }

    /// 404 `lead-not-found`; 409 `stale-data` when the version is not the stored one.
    @PutMapping("/{id}/status")
    LeadResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return LeadResponse.of(leads.changeStatus(id, request.status(), request.version()));
    }

    record StatusRequest(@NotNull Lead.Status status, @NotNull Long version) {
    }
}
