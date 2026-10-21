package com.asp.platform.identity.api;

import jakarta.validation.constraints.NotNull;

import com.asp.platform.shared.Role;

/// @param active false stops the user from logging in
record UpdateUserRequest(@NotNull Role role, @NotNull Boolean active) {
}
