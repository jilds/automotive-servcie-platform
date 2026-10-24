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

/// Packages of services and stock products sold together for one price. Prices are on every field, so mechanics have no access; the
/// front desk reads them to add a package to a work order, the owner changes them.
@RestController
@RequestMapping("/api/v1/catalogue/packages")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
class PackageController {

    private final CatalogueService catalogue;

    PackageController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    /// @param q          part of the name
    /// @param activeOnly true (default) leaves out packages that are no longer offered
    @GetMapping
    List<PackageResponse> list(@RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return catalogue.packages(q, activeOnly).stream().map(PackageResponse::of).toList();
    }

    @GetMapping("/{id}")
    PackageResponse get(@PathVariable UUID id) {
        return PackageResponse.of(catalogue.packageView(id));
    }

    /// The services and products must belong to the workshop (404 `service-not-found`, `product-not-found`) and be
    /// offered (409 `service-inactive`, `product-inactive`).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    PackageResponse create(@Valid @RequestBody PackageRequest request) {
        return PackageResponse.of(catalogue.createPackage(request.toDetails()));
    }

    /// Needs the version the editor read; someone else's newer save answers 409 `stale-data`.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    PackageResponse update(@PathVariable UUID id, @Validated(OnUpdate.class) @RequestBody PackageRequest request) {
        return PackageResponse.of(catalogue.updatePackage(id, request.toDetails(), request.requiredVersion()));
    }
}
