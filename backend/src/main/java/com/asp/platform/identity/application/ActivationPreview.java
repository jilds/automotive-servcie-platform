package com.asp.platform.identity.application;

import java.time.Instant;

/// What the activation page shows before the user sets their password.
public record ActivationPreview(String email, String name, String workshopName, Instant expiresAt) {
}
