package com.asp.platform.catalogue.infrastructure;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataServiceItemRepository extends JpaRepository<ServiceItemEntity, UUID> {

    @Query("""
            select s from ServiceItemEntity s
            where (:q is null
                   or lower(s.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(s.category) like lower(concat('%', cast(:q as string), '%')))
              and (s.active = true or :activeOnly = false)
            order by lower(s.name)""")
    List<ServiceItemEntity> search(@Nullable String q, boolean activeOnly);
}
