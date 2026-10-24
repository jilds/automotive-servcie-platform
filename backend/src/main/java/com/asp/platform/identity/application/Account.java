package com.asp.platform.identity.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.TokenClaims;
import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.User;

/// Who signed in: a workshop user or a platform admin.
///
/// @param tenantId the workshop, or null for a platform admin
/// @param role     a [com.asp.platform.shared.Role] name, or `PLATFORM_ADMIN`
/// @param pending  true while a workshop user has not opened their activation link
/// @param version  the user row's version (0 for a platform admin); the owner sends it back when changing access
public record Account(UUID id, @Nullable UUID tenantId, String name, String email, String role, boolean active,
        boolean pending, long version) {

    public static Account of(User user) {
        return new Account(user.id(), user.tenantId(), user.name(), user.email(), user.role().name(), user.active(),
                user.pending(), user.version());
    }

    public static Account of(PlatformAdmin admin) {
        return new Account(admin.id(), null, admin.name(), admin.email(), TokenClaims.PLATFORM_ADMIN, admin.active(),
                false, 0);
    }
}
