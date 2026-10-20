package com.asp.customers.internal;

import java.util.List;
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

@RestController
@RequestMapping("/api/v1")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    CustomerDtos.CustomerResponse create(@Valid @RequestBody CustomerDtos.CustomerRequest req) {
        return service.create(req);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    CustomerDtos.CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerDtos.CustomerRequest req) {
        return service.update(id, req);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<CustomerDtos.CustomerResponse> search(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    CustomerDtos.CustomerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/customers/{id}/vehicles")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<CustomerDtos.VehicleResponse> vehiclesOf(@PathVariable UUID id) {
        return service.vehiclesOf(id);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    CustomerDtos.VehicleResponse addVehicle(@Valid @RequestBody CustomerDtos.VehicleRequest req) {
        return service.addVehicle(req);
    }

    /** Mechanics may look up a vehicle, but never the customer's personal data. */
    @GetMapping(value = "/vehicles", params = "plate")
    CustomerDtos.VehicleResponse byPlate(@RequestParam String plate) {
        return service.vehicleByPlate(plate);
    }

    @GetMapping("/vehicles/{id}")
    CustomerDtos.VehicleResponse vehicle(@PathVariable UUID id) {
        return service.vehicle(id);
    }
}
