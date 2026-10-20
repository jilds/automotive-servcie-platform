package com.asp.platform.customer.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleRepository;

@Repository
class JpaVehicleRepository implements VehicleRepository {

    private final SpringDataVehicleRepository jpa;

    JpaVehicleRepository(SpringDataVehicleRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity = jpa.findById(vehicle.id())
                .map(existing -> {
                    existing.apply(vehicle);
                    return existing;
                })
                .orElseGet(() -> jpa.save(new VehicleEntity(vehicle)));
        return entity.toDomain();
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return jpa.findById(id).map(VehicleEntity::toDomain);
    }

    @Override
    public Optional<Vehicle> findByPlate(String normalizedPlate) {
        return jpa.findByPlate(normalizedPlate).map(VehicleEntity::toDomain);
    }

    @Override
    public boolean existsByPlate(String normalizedPlate) {
        return jpa.existsByPlate(normalizedPlate);
    }

    @Override
    public List<Vehicle> findByCustomer(UUID customerId) {
        return jpa.findByCustomerIdOrderByPlate(customerId).stream().map(VehicleEntity::toDomain).toList();
    }
}
