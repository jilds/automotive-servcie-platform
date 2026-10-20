package com.asp.identity.internal;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.asp.shared.security.Role;

final class AuthDtos {

    private AuthDtos() {
    }

    record SignupRequest(@NotBlank String workshopName, String nif, @NotBlank String ownerName,
            @NotBlank @Email String email, @NotBlank @Size(min = 8) String password) {
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    record InviteRequest(@NotBlank String name, @NotBlank @Email String email, @NotNull Role role,
            @NotBlank @Size(min = 8) String temporaryPassword) {
    }

    record UserResponse(UUID id, UUID tenantId, String name, String email, Role role) {
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
