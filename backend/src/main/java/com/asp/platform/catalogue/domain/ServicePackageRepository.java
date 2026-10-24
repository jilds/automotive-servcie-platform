package com.asp.platform.catalogue.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface ServicePackageRepository {

    ServicePackage save(ServicePackage servicePackage);

    Optional<ServicePackage> findById(UUID id);

    /// By name; `query` matches the name, any case; null lists everything.
    List<ServicePackage> search(@Nullable String query, boolean activeOnly);
}
