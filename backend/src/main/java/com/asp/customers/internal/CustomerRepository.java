package com.asp.customers.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Row-level security limits every query to the current workshop. */
interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Query("""
            select c from Customer c
            where :q is null
               or lower(c.name) like lower(concat('%', cast(:q as string), '%'))
               or c.nif like concat('%', cast(:q as string), '%')
               or c.phone like concat('%', cast(:q as string), '%')
            order by c.name""")
    List<Customer> search(String q);
}
