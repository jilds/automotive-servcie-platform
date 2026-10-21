package com.asp.platform.identity.domain;

import java.util.Optional;
import java.util.UUID;

/// The tenant table is not under row-level security: callers always pass the signed-in user's tenant id.
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID id);
}
