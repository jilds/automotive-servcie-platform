package com.asp.platform.identity.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.asp.platform.identity.domain.SubscriptionStatus;

interface SpringDataTenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<TenantEntity> findAllByOrderByCreatedAtDesc();

    @Query("select t from TenantEntity t where t.status in :statuses order by t.createdAt")
    List<TenantEntity> findByStatusIn(@Param("statuses") Collection<SubscriptionStatus> statuses);
}
