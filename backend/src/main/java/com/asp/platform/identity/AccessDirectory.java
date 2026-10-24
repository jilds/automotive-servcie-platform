package com.asp.platform.identity;

import java.util.Optional;
import java.util.UUID;

/// Public API of the identity module for request authentication: a user's current role and whether they
/// may still sign in. Tokens live for hours, so the security wiring checks every request against this
/// instead of trusting the role and state written into the token.
///
/// Every answer may be up to a few seconds stale on other application instances; changes made on this instance
/// apply to the next request.
public interface AccessDirectory {

    /// A workshop user's current access, or empty when no such user exists.
    Optional<UserAccess> access(UUID userId);

    /// True while the workshop's subscription lets its users sign in (trial or active); false when it is
    /// suspended, cancelled or unknown.
    boolean workshopOpen(UUID tenantId);

    /// True when the id is an active platform admin.
    boolean isActivePlatformAdmin(UUID adminId);
}
