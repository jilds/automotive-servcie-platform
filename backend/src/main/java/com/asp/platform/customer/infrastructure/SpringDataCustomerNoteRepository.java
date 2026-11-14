package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCustomerNoteRepository extends JpaRepository<CustomerNoteEntity, UUID> {

    List<CustomerNoteEntity> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId);
}
