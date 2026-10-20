package com.asp.platform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.asp.platform.identity.application.InviteUserCommand;
import com.asp.platform.shared.Role;

record InviteUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull Role role,
        @NotBlank @Size(min = 8) String temporaryPassword) {

    InviteUserCommand toCommand() {
        return new InviteUserCommand(name, email, role, temporaryPassword);
    }
}
