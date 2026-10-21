package com.asp.platform.identity;

import java.util.Optional;
import java.util.UUID;

/// Public API of the identity module for request authentication: a user's current role and whether they
/// may still sign in. Tokens live for hours, so the security wiring checks every request against this
/// instead of trusting the role and state written into the token.
public interface AccessDirectory {

    /// The user's current access, or empty when no such user exists. May be up to a few seconds stale
    /// on other application instances; changes made on this instance apply to the next request.
    Optional<UserAccess> access(UUID userId);
}
