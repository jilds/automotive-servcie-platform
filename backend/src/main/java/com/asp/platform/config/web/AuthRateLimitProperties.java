package com.asp.platform.config.web;

import java.time.Duration;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.asp.platform.config.web.PublicRateLimitProperties.Rule;

/// Limits on the sign-in endpoints (`POST /auth/login`, `POST /auth/activate`, `GET /auth/activation`).
///
/// @param attempts requests to those endpoints from one client address, across all of them: 10 a minute by default
/// @param failures failed logins (401) for one submitted e-mail, from any address: 5 in 15 minutes by default. The
///                 e-mail is counted by its SHA-256 whether or not an account exists, so a refusal never tells which
///                 e-mails are registered
@Validated
@ConfigurationProperties("app.auth.rate-limit")
public record AuthRateLimitProperties(@Valid @Nullable Rule attempts,
        @Valid @Nullable Rule failures) {

    private static final Rule DEFAULT_ATTEMPTS =
            new Rule(10, Duration.ofMinutes(1));
    private static final Rule DEFAULT_FAILURES =
            new Rule(5, Duration.ofMinutes(15));

    public AuthRateLimitProperties {
        attempts = attempts == null ? DEFAULT_ATTEMPTS : attempts;
        failures = failures == null ? DEFAULT_FAILURES : failures;
    }
}
