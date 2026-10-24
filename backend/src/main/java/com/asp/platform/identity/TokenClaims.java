package com.asp.platform.identity;

/// Names of the claims identity puts in every access token. Read by the app-wide security and tenancy wiring.
public final class TokenClaims {

    /// The workshop the user belongs to. Absent for a platform admin, so a platform admin's connections never
    /// see a workshop's rows under row-level security.
    public static final String TENANT_ID = "tenant_id";

    /// One of the [com.asp.platform.shared.Role] names, or [#PLATFORM_ADMIN].
    public static final String ROLE = "role";

    public static final String EMAIL = "email";

    /// Role claim of a platform admin. Not a [com.asp.platform.shared.Role]: platform admins are not workshop users.
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private TokenClaims() {
    }
}
