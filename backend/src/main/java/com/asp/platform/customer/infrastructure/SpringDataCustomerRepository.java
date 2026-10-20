package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataCustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    @Query("""
            select c from CustomerEntity c
            where :q is null
               or lower(c.name) like lower(concat('%', cast(:q as string), '%'))
               or c.nif like concat('%', cast(:q as string), '%')
               or c.phone like concat('%', cast(:q as string), '%')
            order by c.name""")
    List<CustomerEntity> search(@Nullable String q);
}
