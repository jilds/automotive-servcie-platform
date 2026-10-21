package com.asp.platform.identity;

import java.util.UUID;

import com.asp.platform.shared.Role;

/// A user's current access as stored, which may differ from what an older token says.
///
/// @param tenantId the workshop the user belongs to
/// @param role     the user's current role
/// @param active   false once an owner has deactivated the user
public record UserAccess(UUID tenantId, Role role, boolean active) {
}
