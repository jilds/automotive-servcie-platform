package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVehicleRepository extends JpaRepository<VehicleEntity, UUID> {

    Optional<VehicleEntity> findByPlate(String plate);

    boolean existsByPlate(String plate);

    /// VINs are not unique (older records were never checked), so the most recently changed vehicle wins.
    Optional<VehicleEntity> findFirstByVinOrderByUpdatedAtDesc(String vin);

    List<VehicleEntity> findByCustomerIdOrderByPlate(UUID customerId);
}
