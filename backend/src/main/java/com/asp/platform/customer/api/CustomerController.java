package com.asp.platform.customer.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
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
import com.asp.platform.shared.CurrentUser;

/// Customer personal data is for the owner and the front desk only.
@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
class CustomerController {

    private final CustomerService customers;
    private final PlateFormats plates;

    CustomerController(CustomerService customers, PlateFormats plates) {
        this.customers = customers;
        this.plates = plates;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse create(CurrentUser user, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.of(customers.create(user.tenantId(), request.toContactDetails()));
    }

    /// Needs the version the editor read; someone else's newer save answers 409 `stale-data`.
    @PutMapping("/{id}")
    CustomerResponse update(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody CustomerUpdateRequest request) {
        return CustomerResponse.of(
                customers.update(user.tenantId(), id, request.toContactDetails(), request.version()));
    }

    @GetMapping
    List<CustomerResponse> search(@RequestParam(required = false) @Nullable String q) {
        return customers.search(q).stream().map(CustomerResponse::of).toList();
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.of(customers.get(id));
    }

    @GetMapping("/{id}/vehicles")
    List<VehicleResponse> vehiclesOf(@PathVariable UUID id) {
        return customers.vehiclesOf(id).stream().map(vehicle -> VehicleResponse.of(vehicle, plates)).toList();
    }
}
