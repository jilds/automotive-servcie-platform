package com.asp.platform.identity;

/// Names of the claims identity puts in every access token. Read by the app-wide security and tenancy wiring.
public final class TokenClaims {

    /// The workshop the user belongs to.
    public static final String TENANT_ID = "tenant_id";

    /// One of the [com.asp.platform.shared.Role] names.
    public static final String ROLE = "role";

    public static final String EMAIL = "email";

    private TokenClaims() {
    }
}
