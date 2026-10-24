package com.asp.platform.catalogue.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface ServiceItemRepository {

    ServiceItem save(ServiceItem item);

    Optional<ServiceItem> findById(UUID id);

    /// One query; ids not found are left out.
    List<ServiceItem> findAllById(Collection<UUID> ids);

    /// By name; `query` matches name or category, any case; null lists everything.
    List<ServiceItem> search(@Nullable String query, boolean activeOnly);
}
