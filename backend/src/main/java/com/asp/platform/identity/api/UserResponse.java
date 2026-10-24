package com.asp.platform.identity.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.Account;
import com.asp.platform.identity.domain.User;

/// @param tenantId null for a platform admin
/// @param role     `OWNER`, `FRONT_DESK`, `MECHANIC` or `PLATFORM_ADMIN`
/// @param pending  true until the user has opened their activation link and set a password
/// @param version  sent back with `PUT /users/{id}` (optimistic locking)
record UserResponse(UUID id, @Nullable UUID tenantId, String name, String email, String role, boolean active,
        boolean pending, long version) {

    static UserResponse of(User user) {
        return of(Account.of(user));
    }

    static UserResponse of(Account a) {
        return new UserResponse(a.id(), a.tenantId(), a.name(), a.email(), a.role(), a.active(), a.pending(),
                a.version());
    }
}
