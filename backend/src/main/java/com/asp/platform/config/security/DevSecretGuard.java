package com.asp.platform.config.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/// Stops the start when the development JWT secret would sign tokens outside an active `dev` or `test` profile:
/// anyone who read the repository could forge a token for any workshop. The secret lives only in
/// `application-dev.yml`; `application.yml` has no fallback, so a run without a profile and without `ASP_JWT_SECRET`
/// already fails on the empty secret. This guard also catches the dev value pasted into another environment.
@Component
class DevSecretGuard {

    /// The fallback of `app.security.jwt-secret` in `application-dev.yml` (keep the two equal); public, so never
    /// valid outside dev and test.
    static final String DEV_SECRET = "dev-only-secret-change-me-at-least-32-bytes!!";

    /// The fallback of `app.billing.payments.webhook-secret` in `application-dev.yml` (keep the two equal); public, so
    /// never valid outside dev and test.
    static final String DEV_WEBHOOK_SECRET = "dev-only-webhook-secret-change-me-32-bytes!!";

    /// Dev secrets are valid only in dev or test, and never when prod is active as well.
    private static boolean refusesDevSecrets(Environment environment) {
        return environment.matchesProfiles("prod") || !environment.matchesProfiles("dev | test");
    }

    DevSecretGuard(AppSecurityProperties properties, Environment environment) {
        if (DEV_SECRET.equals(properties.jwtSecret()) && refusesDevSecrets(environment)) {
            throw new IllegalStateException("app.security.jwt-secret is the development secret; set ASP_JWT_SECRET "
                    + "to a random value of at least 32 characters");
        }
        String webhookSecret = environment.getProperty("app.billing.payments.webhook-secret");
        if (DEV_WEBHOOK_SECRET.equals(webhookSecret) && refusesDevSecrets(environment)) {
            throw new IllegalStateException("app.billing.payments.webhook-secret is the development secret; set "
                    + "ASP_BILLING_WEBHOOK_SECRET to a random value of at least 32 bytes");
        }
    }
}
