package com.asp.platform.customer.domain;

import com.asp.platform.shared.NotFoundException;

public class VehicleNotFoundException extends NotFoundException {

    public VehicleNotFoundException() {
        super("vehicle-not-found", "Vehicle not found");
    }

    public VehicleNotFoundException(String plate) {
        super("vehicle-not-found", "No vehicle with plate " + plate);
    }
}
