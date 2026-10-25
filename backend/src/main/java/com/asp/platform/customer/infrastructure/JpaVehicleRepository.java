package com.asp.platform.customer.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.asp.platform.customer.domain.DuplicatePlateException;
import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleRepository;
import com.asp.platform.shared.persistence.Constraints;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaVehicleRepository implements VehicleRepository {

    /// The `unique (tenant_id, plate)` constraint of V1, named by PostgreSQL's default.
    static final String UNIQUE_PLATE_CONSTRAINT = "vehicle_tenant_id_plate_key";

    private final SpringDataVehicleRepository jpa;

    JpaVehicleRepository(SpringDataVehicleRepository jpa) {
        this.jpa = jpa;
    }

    /// Flushes at once: the service's "plate already taken?" check runs before the write, so two saves of the
    /// same plate at the same time are only caught by the unique constraint, which becomes a 409 here
    /// instead of a 500 at commit.
    @Override
    public Vehicle save(Vehicle vehicle) {
        try {
            return Upserts.upsert(jpa, vehicle.id(), vehicle, VehicleEntity::new);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.violated(e, UNIQUE_PLATE_CONSTRAINT)) {
                throw new DuplicatePlateException(vehicle.details().plate());
            }
            throw e;
        }
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return jpa.findById(id).map(VehicleEntity::toDomain);
    }

    @Override
    public List<Vehicle> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(VehicleEntity::toDomain).toList();
    }

    @Override
    public Optional<Vehicle> findByPlate(String normalizedPlate) {
        return jpa.findByPlate(normalizedPlate).map(VehicleEntity::toDomain);
    }

    @Override
    public List<Vehicle> findByPlates(Collection<String> normalizedPlates) {
        return jpa.findByPlateIn(normalizedPlates).stream().map(VehicleEntity::toDomain).toList();
    }

    @Override
    public Optional<Vehicle> findByVin(String normalizedVin) {
        return jpa.findFirstByVinOrderByUpdatedAtDesc(normalizedVin).map(VehicleEntity::toDomain);
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
