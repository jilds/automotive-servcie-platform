package com.asp.platform.customer.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.customer.application.CustomerService;

@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController {

    private final CustomerService customers;

    VehicleController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    VehicleResponse add(@Valid @RequestBody VehicleRequest request) {
        return VehicleResponse.of(customers.addVehicle(request.customerId(), request.toDetails(), request.mileage()));
    }

    /// Mechanics may look up a vehicle, but never the customer's personal data.
    @GetMapping(params = "plate")
    VehicleResponse byPlate(@RequestParam String plate) {
        return VehicleResponse.of(customers.vehicleByPlate(plate));
    }

    @GetMapping("/{id}")
    VehicleResponse get(@PathVariable UUID id) {
        return VehicleResponse.of(customers.vehicle(id));
    }
}
