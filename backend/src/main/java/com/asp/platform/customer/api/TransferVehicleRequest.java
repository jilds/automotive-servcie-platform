package com.asp.platform.customer.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

record TransferVehicleRequest(@NotNull UUID customerId) {
}
