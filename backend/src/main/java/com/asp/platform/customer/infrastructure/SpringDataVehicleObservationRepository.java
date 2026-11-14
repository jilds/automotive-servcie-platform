package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVehicleObservationRepository extends JpaRepository<VehicleObservationEntity, UUID> {

    List<VehicleObservationEntity> findByVehicleIdOrderByCreatedAtDescIdDesc(UUID vehicleId);
}
