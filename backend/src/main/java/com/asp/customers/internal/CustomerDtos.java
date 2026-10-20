package com.asp.customers.internal;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class CustomerDtos {

    private CustomerDtos() {
    }

    record CustomerRequest(@NotBlank String name, @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
            String phone, @Email String email, String address, Boolean marketingConsent) {
    }

    record CustomerResponse(UUID id, String name, String nif, String phone, String email, String address,
            boolean marketingConsent) {
        static CustomerResponse of(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getNif(), c.getPhone(), c.getEmail(), c.getAddress(),
                    c.isMarketingConsent());
        }
    }

    record VehicleRequest(@NotNull UUID customerId, @NotBlank String plate, @Size(min = 17, max = 17) String vin,
            String make, String model, @Min(1950) @Max(2100) Integer year, String fuelType,
            @PositiveOrZero Integer mileage) {
    }

    record VehicleResponse(UUID id, UUID customerId, String plate, String vin, String make, String model,
            Integer year, String fuelType, Integer mileage) {
        static VehicleResponse of(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getCustomerId(), v.getPlate(), v.getVin(), v.getMake(),
                    v.getModel(), v.getYear(), v.getFuelType(), v.getMileage());
        }
    }
}
