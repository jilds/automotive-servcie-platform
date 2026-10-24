package com.asp.platform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.asp.platform.identity.application.InviteUserCommand;
import com.asp.platform.shared.Role;

/// Release 3: no password any more; the response carries an activation link instead.
record InviteUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Role role) {

    InviteUserCommand toCommand() {
        return new InviteUserCommand(name, email, role);
    }
}
