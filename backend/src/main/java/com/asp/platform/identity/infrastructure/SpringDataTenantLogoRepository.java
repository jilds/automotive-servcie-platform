package com.asp.platform.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataTenantLogoRepository extends JpaRepository<TenantLogoEntity, UUID> {

    @Query("select l.sha256 from TenantLogoEntity l where l.tenantId = :tenantId")
    Optional<String> findHash(UUID tenantId);
}
