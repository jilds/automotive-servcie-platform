package com.asp.customers.internal;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.customers.VehicleDirectory;
import com.asp.customers.VehicleSummary;
import com.asp.shared.web.ConflictException;

@Service
class CustomerService implements VehicleDirectory {

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;

    CustomerService(CustomerRepository customers, VehicleRepository vehicles) {
        this.customers = customers;
        this.vehicles = vehicles;
    }

    @Transactional
    CustomerDtos.CustomerResponse create(CustomerDtos.CustomerRequest r) {
        return CustomerDtos.CustomerResponse.of(customers.save(
                new Customer(r.name(), r.nif(), r.phone(), r.email(), r.address(), Boolean.TRUE.equals(r.marketingConsent()))));
    }

    @Transactional
    CustomerDtos.CustomerResponse update(UUID id, CustomerDtos.CustomerRequest r) {
        Customer c = customer(id);
        c.update(r.name(), r.nif(), r.phone(), r.email(), r.address(), Boolean.TRUE.equals(r.marketingConsent()));
        return CustomerDtos.CustomerResponse.of(c);
    }

    @Transactional(readOnly = true)
    List<CustomerDtos.CustomerResponse> search(String q) {
        return customers.search(q == null || q.isBlank() ? null : q.trim()).stream()
                .map(CustomerDtos.CustomerResponse::of).toList();
    }

    @Transactional(readOnly = true)
    CustomerDtos.CustomerResponse get(UUID id) {
        return CustomerDtos.CustomerResponse.of(customer(id));
    }

    @Transactional
    CustomerDtos.VehicleResponse addVehicle(CustomerDtos.VehicleRequest r) {
        customer(r.customerId());
        if (vehicles.existsByPlate(Vehicle.normalizePlate(r.plate()))) {
            throw new ConflictException("A vehicle with plate " + r.plate() + " already exists");
        }
        return CustomerDtos.VehicleResponse.of(vehicles.save(new Vehicle(r.customerId(), r.plate(), r.vin(),
                r.make(), r.model(), r.year(), r.fuelType(), r.mileage())));
    }

    @Transactional(readOnly = true)
    CustomerDtos.VehicleResponse vehicleByPlate(String plate) {
        return vehicles.findByPlate(Vehicle.normalizePlate(plate)).map(CustomerDtos.VehicleResponse::of)
                .orElseThrow(() -> new NoSuchElementException("No vehicle with plate " + plate));
    }

    @Transactional(readOnly = true)
    CustomerDtos.VehicleResponse vehicle(UUID id) {
        return vehicles.findById(id).map(CustomerDtos.VehicleResponse::of)
                .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));
    }

    @Transactional(readOnly = true)
    List<CustomerDtos.VehicleResponse> vehiclesOf(UUID customerId) {
        return vehicles.findByCustomerIdOrderByPlate(customerId).stream().map(CustomerDtos.VehicleResponse::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleSummary> findVehicle(UUID vehicleId) {
        return vehicles.findById(vehicleId).map(v -> new VehicleSummary(v.getId(), v.getCustomerId(), v.getPlate(),
                v.getVin(), v.getMake(), v.getModel(), v.getYear(), v.getMileage()));
    }

    @Override
    @Transactional
    public void recordMileage(UUID vehicleId, int mileage) {
        vehicles.findById(vehicleId).ifPresent(v -> v.recordMileage(mileage));
    }

    private Customer customer(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NoSuchElementException("Customer not found"));
    }
}
