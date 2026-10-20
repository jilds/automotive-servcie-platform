package com.asp.platform.customer.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.customer.VehicleSummary;
import com.asp.platform.customer.domain.ContactDetails;
import com.asp.platform.customer.domain.Customer;
import com.asp.platform.customer.domain.CustomerNotFoundException;
import com.asp.platform.customer.domain.CustomerRepository;
import com.asp.platform.customer.domain.DuplicatePlateException;
import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;
import com.asp.platform.customer.domain.VehicleNotFoundException;
import com.asp.platform.customer.domain.VehicleRepository;

@Service
public class CustomerService implements CustomerManagement {

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;

    CustomerService(CustomerRepository customers, VehicleRepository vehicles) {
        this.customers = customers;
        this.vehicles = vehicles;
    }

    @Transactional
    public Customer create(ContactDetails contact) {
        return customers.save(new Customer(UUID.randomUUID(), contact));
    }

    @Transactional
    public Customer update(UUID id, ContactDetails contact) {
        Customer customer = customer(id);
        customer.update(contact);
        return customers.save(customer);
    }

    @Transactional(readOnly = true)
    public List<Customer> search(@Nullable String query) {
        return customers.search(query == null || query.isBlank() ? null : query.trim());
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return customer(id);
    }

    @Transactional
    public Vehicle addVehicle(UUID customerId, VehicleDetails details, @Nullable Integer mileage) {
        customer(customerId);
        if (vehicles.existsByPlate(details.plate())) {
            throw new DuplicatePlateException(details.plate());
        }
        return vehicles.save(new Vehicle(UUID.randomUUID(), customerId, details, mileage));
    }

    @Transactional(readOnly = true)
    public Vehicle vehicleByPlate(String plate) {
        return vehicles.findByPlate(VehicleDetails.normalizePlate(plate))
                .orElseThrow(() -> new VehicleNotFoundException(plate));
    }

    @Transactional(readOnly = true)
    public Vehicle vehicle(UUID id) {
        return vehicles.findById(id).orElseThrow(VehicleNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> vehiclesOf(UUID customerId) {
        return vehicles.findByCustomer(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleSummary> findVehicle(UUID vehicleId) {
        return vehicles.findById(vehicleId).map(CustomerService::summary);
    }

    @Override
    @Transactional
    public void recordMileage(UUID vehicleId, int mileage) {
        vehicles.findById(vehicleId).ifPresent(vehicle -> {
            vehicle.recordMileage(mileage);
            vehicles.save(vehicle);
        });
    }

    private Customer customer(UUID id) {
        return customers.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

    private static VehicleSummary summary(Vehicle v) {
        VehicleDetails d = v.details();
        return new VehicleSummary(v.id(), v.customerId(), d.plate(), d.vin(), d.make(), d.model(), d.year(),
                v.mileage());
    }
}
