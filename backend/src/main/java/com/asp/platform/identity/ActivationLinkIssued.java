package com.asp.platform.identity;

import java.time.Instant;
import java.util.UUID;

/// Published when an activation link is issued: a new workshop's first owner, an invited team member, or a link sent
/// again. Listeners run after the commit (the notification module mails it). Carries the raw link once; never log it.
///
/// @param path      the link relative to the app, `/activate?token=…`
/// @param firstOwner true for the owner of a workshop the platform admin just registered (or their link sent again)
public record ActivationLinkIssued(UUID tenantId, String email, String name, String path, Instant expiresAt,
        boolean firstOwner) {

    @Override
    public String toString() {
        // Keep the token and the address out of logs.
        return "ActivationLinkIssued[tenantId=" + tenantId + ", firstOwner=" + firstOwner + "]";
    }
}
