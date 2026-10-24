package com.asp.platform.identity.domain;

import java.util.Optional;
import java.util.UUID;

/// Workshop logos, kept apart from the tenant row. Not under row-level security (the branded login page reads them
/// before anyone signs in); callers pass the signed-in owner's tenant, or a tenant found by its public slug.
public interface TenantLogoRepository {

    void save(UUID tenantId, Logo logo);

    Optional<Logo> find(UUID tenantId);

    /// The logo's hash without loading the image, for building cache-busting URLs.
    Optional<String> hashOf(UUID tenantId);

    void delete(UUID tenantId);
}
