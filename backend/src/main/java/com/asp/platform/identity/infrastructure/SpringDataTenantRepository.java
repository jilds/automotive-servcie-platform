package com.asp.platform.identity.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantRepository extends JpaRepository<TenantEntity, UUID> {
}
