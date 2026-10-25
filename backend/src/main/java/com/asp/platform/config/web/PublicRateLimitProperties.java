package com.asp.platform.config.web;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// Requests one client address may make to the public pages (ADR 0004).
///
/// @param lookups every public request of one address, across all slugs, counted before the slug is looked up:
///                120 a minute by default, so unknown or closed slugs cannot be flooded either
/// @param reads   GET, HEAD and OPTIONS to one workshop: 120 a minute by default
/// @param writes  POST, PUT, PATCH and DELETE to one workshop: 10 an hour by default
@Validated
@ConfigurationProperties("app.public.rate-limit")
public record PublicRateLimitProperties(@Valid @NotNull Rule lookups, @Valid @NotNull Rule reads,
        @Valid @NotNull Rule writes) {

    /// @param limit  requests allowed per window
    /// @param window length of the fixed window, which starts with the first request
    public record Rule(@Positive int limit, @NotNull Duration window) {
    }
}
