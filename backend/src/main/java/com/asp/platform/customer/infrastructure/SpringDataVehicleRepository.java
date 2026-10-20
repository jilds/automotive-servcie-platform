package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVehicleRepository extends JpaRepository<VehicleEntity, UUID> {

    Optional<VehicleEntity> findByPlate(String plate);

    boolean existsByPlate(String plate);

    List<VehicleEntity> findByCustomerIdOrderByPlate(UUID customerId);
}
