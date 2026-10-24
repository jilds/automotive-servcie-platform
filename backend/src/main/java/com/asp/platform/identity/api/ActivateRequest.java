package com.asp.platform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// @param password 12 to 72 characters
record ActivateRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 12, max = 72, message = "Password must have 12 to 72 characters") String password) {

    @Override
    public String toString() {
        return "ActivateRequest[name=" + name + "]";
    }
}
