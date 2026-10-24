package com.asp.platform.customer.domain;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

public final class Vehicle {

    private final UUID id;
    private final long version;
    private UUID customerId;
    private VehicleDetails details;
    private VehicleRegistration registration;
    private @Nullable Integer mileage;

    /// A vehicle not saved yet (version 0).
    public Vehicle(UUID id, UUID customerId, VehicleDetails details, VehicleRegistration registration,
            @Nullable Integer mileage) {
        this(id, customerId, details, registration, mileage, 0);
    }

    /// Rebuilds a vehicle as stored. The plate is not checked here, so plates saved before plate formats
    /// existed can still be read; [#register] and [#edit] check what is written.
    ///
    /// @param version how many times the stored record has changed; edits must name the version they read
    public Vehicle(UUID id, UUID customerId, VehicleDetails details, VehicleRegistration registration,
            @Nullable Integer mileage, long version) {
        this.id = id;
        this.version = version;
        this.customerId = customerId;
        this.details = details;
        this.registration = registration;
        this.mileage = mileage;
    }

    /// A vehicle new to the workshop. Its plate must fit the format of its registration country.
    ///
    /// @throws InvalidPlateException when it does not
    public static Vehicle register(UUID id, UUID customerId, VehicleDetails details,
            VehicleRegistration registration, @Nullable Integer mileage, PlateFormats plates) {
        plates.check(registration.country(), details.plate());
        return new Vehicle(id, customerId, details, registration, mileage);
    }

    /// Odometers only go up: a lower reading (a typo, a swapped dashboard) is ignored.
    public void recordMileage(int reading) {
        if (mileage == null || reading > mileage) {
            mileage = reading;
        }
    }

    /// Corrects what the front desk entered. A null mileage keeps the current reading; a reading below
    /// the recorded one is refused, because odometers only go up. A changed plate or registration country
    /// must fit that country's plate format; an unchanged one is kept as it is, even if it predates the
    /// format check.
    ///
    /// @throws InvalidPlateException     when a changed plate or country does not fit
    /// @throws MileageDecreaseException when the mileage would go down
    public void edit(VehicleDetails newDetails, VehicleRegistration newRegistration, @Nullable Integer newMileage,
            PlateFormats plates) {
        if (!hasPlate(newDetails.plate()) || !registration.isIn(newRegistration.country())) {
            plates.check(newRegistration.country(), newDetails.plate());
        }
        if (newMileage != null && mileage != null && newMileage < mileage) {
            throw new MileageDecreaseException(mileage, newMileage);
        }
        this.details = newDetails;
        this.registration = newRegistration;
        if (newMileage != null) {
            this.mileage = newMileage;
        }
    }

    /// The car was sold or registered to someone else. Its service history stays with the vehicle.
    public void transferTo(UUID newCustomerId) {
        this.customerId = newCustomerId;
    }

    /// @param expectedVersion the version the editor read
    /// @throws StaleDataException when someone else saved the vehicle since (an edit, a transfer, or a check-in
    ///                            that raised the mileage)
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    public boolean hasPlate(String normalizedPlate) {
        return details.plate().equals(normalizedPlate);
    }

    /// The plate as shown to people, with its numbering series when the country's format knows it.
    public FormattedPlate formattedPlate(PlateFormats plates) {
        return plates.format(registration.country(), details.plate());
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    public UUID customerId() {
        return customerId;
    }

    public VehicleDetails details() {
        return details;
    }

    public VehicleRegistration registration() {
        return registration;
    }

    public @Nullable Integer mileage() {
        return mileage;
    }
}
