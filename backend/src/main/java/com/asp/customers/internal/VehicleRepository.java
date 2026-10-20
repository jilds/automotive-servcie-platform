package com.asp.customers.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByPlate(String plate);

    boolean existsByPlate(String plate);

    List<Vehicle> findByCustomerIdOrderByPlate(UUID customerId);
}
