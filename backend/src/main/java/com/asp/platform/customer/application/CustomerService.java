package com.asp.platform.customer.application;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.asp.platform.customer.domain.FormattedPlate;
import com.asp.platform.customer.domain.InvalidVinException;
import com.asp.platform.customer.domain.PlateEra;
import com.asp.platform.customer.domain.PlateFormats;
import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.customer.domain.VehicleDetails;
import com.asp.platform.customer.domain.VehicleNotFoundException;
import com.asp.platform.customer.domain.VehicleRegistration;
import com.asp.platform.customer.domain.VehicleRepository;
import com.asp.platform.identity.WorkshopDirectory;

@Service
public class CustomerService implements CustomerManagement {

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final WorkshopDirectory workshops;
    private final PlateFormats plates;

    CustomerService(CustomerRepository customers, VehicleRepository vehicles, WorkshopDirectory workshops,
            PlateFormats plates) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.workshops = workshops;
        this.plates = plates;
    }

    /// The NIF is checked against the address country, else the workshop's country.
    ///
    /// @throws com.asp.platform.shared.InvalidNifException when a Portuguese NIF is not valid
    @Transactional
    public Customer create(UUID tenantId, ContactDetails contact) {
        contact.checkNif(workshops.country(tenantId));
        return customers.save(new Customer(UUID.randomUUID(), contact));
    }

    /// Also re-checks the NIF, so a NIF stored before the check existed must be corrected on the next edit.
    ///
    /// @param expectedVersion the version the editor read
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the customer since
    @Transactional
    public Customer update(UUID tenantId, UUID id, ContactDetails contact, long expectedVersion) {
        Customer customer = customer(id);
        customer.requireVersion(expectedVersion);
        contact.checkNif(workshops.country(tenantId));
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

    /// Adds a vehicle to a customer. Without a registration country the vehicle is taken to be registered
    /// in the workshop's country. The plate must fit that country's format.
    @Transactional
    public Vehicle addVehicle(UUID tenantId, UUID customerId, VehicleDetails details,
            @Nullable String registrationCountry, @Nullable YearMonth registrationDate, @Nullable Integer mileage) {
        customer(customerId);
        String country = registrationCountry == null ? workshops.country(tenantId) : registrationCountry;
        Vehicle vehicle = Vehicle.register(UUID.randomUUID(), customerId, details,
                new VehicleRegistration(country, registrationDate), mileage, plates);
        if (vehicles.existsByPlate(details.plate())) {
            throw new DuplicatePlateException(details.plate());
        }
        return vehicles.save(vehicle);
    }

    /// Corrects a vehicle's details. A new plate must not belong to another vehicle of the workshop.
    /// Without a registration country the vehicle keeps the one it has.
    @Transactional
    public Vehicle updateVehicle(UUID id, VehicleDetails details, @Nullable String registrationCountry,
            @Nullable YearMonth registrationDate, @Nullable Integer mileage, long expectedVersion) {
        Vehicle vehicle = vehicle(id);
        vehicle.requireVersion(expectedVersion);
        String country = registrationCountry == null ? vehicle.registration().country() : registrationCountry;
        if (!vehicle.hasPlate(details.plate()) && vehicles.existsByPlate(details.plate())) {
            throw new DuplicatePlateException(details.plate());
        }
        vehicle.edit(details, new VehicleRegistration(country, registrationDate), mileage, plates);
        return vehicles.save(vehicle);
    }

    /// Moves a vehicle to another customer of the workshop; its work orders stay with it.
    @Transactional
    public Vehicle transferVehicle(UUID id, UUID newCustomerId, long expectedVersion) {
        Vehicle vehicle = vehicle(id);
        vehicle.requireVersion(expectedVersion);
        customer(newCustomerId);
        vehicle.transferTo(newCustomerId);
        return vehicles.save(vehicle);
    }

    @Transactional(readOnly = true)
    public Vehicle vehicleByPlate(String plate) {
        return vehicles.findByPlate(VehicleDetails.normalizePlate(plate))
                .orElseThrow(() -> new VehicleNotFoundException(plate));
    }

    /// @throws InvalidVinException when the VIN does not have 17 characters
    @Transactional(readOnly = true)
    public Vehicle vehicleByVin(String vin) {
        String normalized = VehicleDetails.normalizeVin(vin);
        if (normalized.length() != VehicleDetails.VIN_LENGTH) {
            throw new InvalidVinException();
        }
        return vehicles.findByVin(normalized).orElseThrow(VehicleNotFoundException::byVin);
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
        return vehicles.findById(vehicleId).map(this::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VehicleSummary> findVehicles(Collection<UUID> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }
        return vehicles.findAllById(vehicleIds).stream()
                .collect(Collectors.toUnmodifiableMap(Vehicle::id, this::summary));
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

    private VehicleSummary summary(Vehicle v) {
        VehicleDetails d = v.details();
        FormattedPlate plate = v.formattedPlate(plates);
        PlateEra era = plate.era();
        VehicleRegistration registration = v.registration();
        return new VehicleSummary(v.id(), v.customerId(), d.plate(), d.vin(), d.make(), d.model(), d.year(),
                v.mileage(), plate.display(), era == null ? null : era.name(), registration.country(),
                registration.date());
    }
}
