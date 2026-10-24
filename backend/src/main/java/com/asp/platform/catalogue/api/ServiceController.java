package com.asp.platform.catalogue.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.catalogue.application.CatalogueService;
import com.asp.platform.shared.CurrentUser;

/// The workshop's standard services. Everyone may read them (mechanics without prices); only the owner changes them.
@RestController
@RequestMapping("/api/v1/catalogue/services")
class ServiceController {

    private final CatalogueService catalogue;

    ServiceController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    /// @param q          part of the name or category
    /// @param activeOnly true (default) leaves out services that are no longer offered
    @GetMapping
    List<ServiceResponse> list(CurrentUser user, @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return catalogue.services(q, activeOnly).stream()
                .map(view -> ServiceResponse.of(view, user.canSeePrices()))
                .toList();
    }

    @GetMapping("/{id}")
    ServiceResponse get(CurrentUser user, @PathVariable UUID id) {
        return ServiceResponse.of(catalogue.service(id), user.canSeePrices());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
        return ServiceResponse.of(catalogue.createService(request.toDetails()), true);
    }

    /// Needs the version the editor read; someone else's newer save answers 409 `stale-data`.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    ServiceResponse update(@PathVariable UUID id, @Validated(OnUpdate.class) @RequestBody ServiceRequest request) {
        return ServiceResponse.of(catalogue.updateService(id, request.toDetails(), request.requiredVersion()), true);
    }
}
