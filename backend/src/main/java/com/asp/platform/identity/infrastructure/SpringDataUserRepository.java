package com.asp.platform.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.asp.platform.shared.Role;

interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserEntity> findByTenantIdOrderByName(UUID tenantId);

    Optional<UserEntity> findFirstByTenantIdAndRoleOrderByCreatedAtAscIdAsc(UUID tenantId, Role role);

    /// `select ... for update` on the whole team; ordered by id so concurrent lockers take locks in the same order.
    /// Waits at most 3 s for the locks; then the caller gets a `PessimisticLockingFailureException` (409 `try-again`).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select u from UserEntity u where u.tenantId = :tenantId order by u.id")
    List<UserEntity> lockByTenantId(UUID tenantId);

    @Query("select u.tenantId as tenantId, count(u) as users from UserEntity u group by u.tenantId")
    List<TenantUserCount> countByTenant();

    @Query("""
            select u.tenantId as tenantId, u.email as email from UserEntity u
            where u.role = com.asp.platform.shared.Role.OWNER order by u.createdAt, u.id""")
    List<TenantOwner> ownersByCreation();

    interface TenantUserCount {
        UUID getTenantId();

        long getUsers();
    }

    interface TenantOwner {
        UUID getTenantId();

        String getEmail();
    }
}
