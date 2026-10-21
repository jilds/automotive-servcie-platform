package com.asp.platform.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Users are read before a workshop is known (login), so this table is not under row-level security.
/// Every workshop-facing query filters by tenant explicitly.
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    List<User> findByTenant(UUID tenantId);

    /// Same as [#findByTenant] but locks the rows until the transaction ends, so two owners changing roles
    /// at the same time cannot both remove the last active owner.
    List<User> lockTeam(UUID tenantId);
}
