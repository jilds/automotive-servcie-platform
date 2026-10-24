package com.asp.platform.identity.application;

import java.time.Instant;

/// A freshly issued activation link. The token is in here only once: the database keeps its hash.
///
/// @param token     43 URL-safe characters (32 random bytes, base64url without padding)
/// @param expiresAt when the link stops working
public record ActivationLink(String token, Instant expiresAt) {

    /// The page that opens the link, relative to the app's address. The UI turns it into a full URL to copy.
    public String path() {
        return "/activate?token=" + token;
    }

    @Override
    public String toString() {
        // Never print the token (logs, test failures).
        return "ActivationLink[expiresAt=" + expiresAt + "]";
    }
}
