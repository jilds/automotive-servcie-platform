package com.asp.platform.workshop.api;

import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;

import org.jspecify.annotations.Nullable;

/// `POST /work-orders/{id}/lines/from-catalogue`: exactly one of a catalogue service or package.
record CatalogueLineRequest(@Nullable UUID serviceId, @Nullable UUID packageId) {

    /// Reported as `errors.serviceOrPackage` when both or neither are given.
    @AssertTrue(message = "Give either a service or a package")
    boolean isServiceOrPackage() {
        return (serviceId == null) != (packageId == null);
    }
}
