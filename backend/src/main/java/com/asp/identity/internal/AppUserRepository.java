package com.asp.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Identity tables are read before a tenant is known (login), so they are not under row-level security.
 * Every tenant-facing query here filters by tenant explicitly.
 */
interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByTenantIdOrderByName(UUID tenantId);
}
