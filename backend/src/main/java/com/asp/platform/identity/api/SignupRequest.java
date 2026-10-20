package com.asp.platform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.SignupCommand;

record SignupRequest(
        @NotBlank String workshopName,
        @Nullable @Pattern(regexp = "\\d{9}", message = "NIF must have 9 digits") String nif,
        @NotBlank String ownerName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password) {

    SignupCommand toCommand() {
        return new SignupCommand(workshopName, nif, ownerName, email, password);
    }
}
