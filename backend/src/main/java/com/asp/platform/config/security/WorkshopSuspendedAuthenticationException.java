package com.asp.platform.config.security;

import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/// A valid token of a user whose workshop's subscription is suspended or cancelled. Answered like any revoked
/// token (401, `WWW-Authenticate: Bearer error="invalid_token"`), plus a problem body with code
/// `workshop-suspended` so the UI can say why ([ProblemBearerEntryPoint]).
class WorkshopSuspendedAuthenticationException extends InvalidBearerTokenException {

    static final String CODE = "workshop-suspended";

    WorkshopSuspendedAuthenticationException() {
        super("The workshop's subscription is suspended");
    }
}
