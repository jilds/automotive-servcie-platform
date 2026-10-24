package com.asp.platform.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<TenantEntity> findAllByOrderByCreatedAtDesc();
}
