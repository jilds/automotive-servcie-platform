package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

public class UnknownVehicleException extends NotFoundException {

    public UnknownVehicleException() {
        super("vehicle-not-found", "Vehicle not found");
    }
}
