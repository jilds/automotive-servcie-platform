package com.asp.platform.workshop.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMechanicChangeRepository extends JpaRepository<MechanicChangeEntity, UUID> {

    List<MechanicChangeEntity> findByWorkOrderIdOrderByChangedAtAscIdAsc(UUID workOrderId);
}
