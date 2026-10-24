package com.asp.platform.identity.api;

import jakarta.validation.constraints.NotNull;

import com.asp.platform.shared.Role;

/// @param active  false stops the user from logging in
/// @param version the version the owner read; a different stored version answers 409 `stale-data`
record UpdateUserRequest(@NotNull Role role, @NotNull Boolean active, @NotNull Long version) {
}
