package com.asp.platform.identity.application;

import com.asp.platform.shared.Role;

public record InviteUserCommand(String name, String email, Role role, String temporaryPassword) {
}
