package com.asp.platform.customer.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.customer.application.CustomerService;
import com.asp.platform.customer.domain.PlateFormats;
import com.asp.platform.customer.domain.Vehicle;
import com.asp.platform.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController {

    private final CustomerService customers;
    private final PlateFormats plates;

    VehicleController(CustomerService customers, PlateFormats plates) {
        this.customers = customers;
        this.plates = plates;
    }

    /// Without a registration country the vehicle is registered in the workshop's country.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    VehicleResponse add(CurrentUser user, @Valid @RequestBody VehicleRequest request) {
        return response(customers.addVehicle(user.tenantId(), request.customerId(), request.toDetails(),
                request.registrationCountry(), request.registrationDate(), request.mileage()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleUpdateRequest request) {
        return response(customers.updateVehicle(id, request.toDetails(), request.registrationCountry(),
                request.registrationDate(), request.mileage()));
    }

    /// Hands the vehicle to another customer; its service history goes with it.
    @PutMapping("/{id}/owner")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    VehicleResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferVehicleRequest request) {
        return response(customers.transferVehicle(id, request.customerId()));
    }

    /// Mechanics may look up a vehicle, but never the customer's personal data.
    @GetMapping(params = "plate")
    VehicleResponse byPlate(@RequestParam String plate) {
        return response(customers.vehicleByPlate(plate));
    }

    @GetMapping("/{id}")
    VehicleResponse get(@PathVariable UUID id) {
        return response(customers.vehicle(id));
    }

    private VehicleResponse response(Vehicle vehicle) {
        return VehicleResponse.of(vehicle, plates);
    }
}
