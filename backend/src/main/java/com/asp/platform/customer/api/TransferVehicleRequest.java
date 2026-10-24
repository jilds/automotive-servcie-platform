package com.asp.platform.customer.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/// @param version the vehicle version the editor read; a different stored version answers 409 `stale-data`
record TransferVehicleRequest(@NotNull UUID customerId, @NotNull Long version) {
}
