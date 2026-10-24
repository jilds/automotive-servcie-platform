package com.asp.platform.customer.domain;

import com.asp.platform.shared.NotFoundException;

public class VehicleNotFoundException extends NotFoundException {

    public VehicleNotFoundException() {
        super("vehicle-not-found", "Vehicle not found");
    }

    public VehicleNotFoundException(String plate) {
        super("vehicle-not-found", "No vehicle with plate " + plate);
    }

    private VehicleNotFoundException(String code, String message) {
        super(code, message);
    }

    /// No vehicle has the VIN. The VIN is not repeated in the message.
    public static VehicleNotFoundException byVin() {
        return new VehicleNotFoundException("vehicle-not-found", "No vehicle with this VIN");
    }
}
