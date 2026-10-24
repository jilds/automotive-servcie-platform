package com.asp.platform.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// The tenant table is not under row-level security. Workshop-facing callers always pass the signed-in user's
/// tenant id; only the platform admin lists all workshops, and the branded login page looks one up by slug.
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID id);

    Optional<Tenant> findBySlug(Slug slug);

    boolean existsBySlug(Slug slug);

    /// Every workshop with its owner and user count, newest first. For the platform admin only.
    List<WorkshopOverview> overview();
}
