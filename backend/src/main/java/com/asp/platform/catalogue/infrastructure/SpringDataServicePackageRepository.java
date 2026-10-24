package com.asp.platform.catalogue.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataServicePackageRepository extends JpaRepository<ServicePackageEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<ServicePackageEntity> findById(UUID id);

    /// Items are fetched in the same query, so listing packages costs one statement.
    @EntityGraph(attributePaths = "items")
    @Query("""
            select p from ServicePackageEntity p
            where (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%')))
              and (p.active = true or :activeOnly = false)
            order by lower(p.name)""")
    List<ServicePackageEntity> search(@Nullable String q, boolean activeOnly);
}
