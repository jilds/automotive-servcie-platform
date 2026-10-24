package com.asp.platform.identity.application;

import com.asp.platform.shared.Role;

/// A new team member; they set their own password through the activation link the invite returns.
public record InviteUserCommand(String name, String email, Role role) {
}
