package com.asp.platform.identity.api;

import java.util.UUID;

import com.asp.platform.identity.domain.User;
import com.asp.platform.shared.Role;

record UserResponse(UUID id, UUID tenantId, String name, String email, Role role) {

    static UserResponse of(User user) {
        return new UserResponse(user.id(), user.tenantId(), user.name(), user.email(), user.role());
    }
}
