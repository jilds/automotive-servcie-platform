package com.asp.platform.shared;

import java.util.UUID;

/// The signed-in user a request acts for. Controllers receive it as a method parameter.
///
/// @param userId   the user's id
/// @param tenantId the workshop the user belongs to
/// @param role     what the user may see and do
public record CurrentUser(UUID userId, UUID tenantId, Role role) {

    public boolean isMechanic() {
        return role == Role.MECHANIC;
    }

    public boolean canSeePrices() {
        return !isMechanic();
    }
}
