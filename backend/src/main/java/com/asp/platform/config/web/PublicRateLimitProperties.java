package com.asp.platform.config.web;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// Requests one client address may make to the public pages (ADR 0004).
///
/// @param lookups every public request of one address, across all slugs, counted before the slug is looked up:
///                120 a minute by default, so unknown or closed slugs cannot be flooded either
/// @param reads   GET, HEAD and OPTIONS to one workshop: 120 a minute by default
/// @param writes  POST, PUT, PATCH and DELETE to one workshop: 10 an hour by default
/// @param webhooks POST to a payment provider's webhook (`/billing/webhooks/`) of one workshop: 600 a minute by
///                default, counted apart because a provider sends many calls from few addresses and must never use up
///                the budget of the public forms (optional)
/// @param webhookLookups POST requests to a webhook path (`/api/v1/public/{slug}/billing/webhooks/...`) of one address,
///                across all slugs, counted before the slug is looked up like `lookups` but apart from it: 120 a minute
///                by default (optional). Other methods on that path count in `lookups`
@Validated
@ConfigurationProperties("app.public.rate-limit")
public record PublicRateLimitProperties(@Valid @NotNull Rule lookups, @Valid @NotNull Rule reads,
        @Valid @NotNull Rule writes, @Valid @Nullable Rule webhooks, @Valid @Nullable Rule webhookLookups) {

    private static final Rule DEFAULT_WEBHOOKS = new Rule(600, Duration.ofMinutes(1));
    private static final Rule DEFAULT_WEBHOOK_LOOKUPS = new Rule(120, Duration.ofMinutes(1));

    public PublicRateLimitProperties {
        webhooks = webhooks == null ? DEFAULT_WEBHOOKS : webhooks;
        webhookLookups = webhookLookups == null ? DEFAULT_WEBHOOK_LOOKUPS : webhookLookups;
    }

    /// @param limit  requests allowed per window
    /// @param window length of the fixed window, which starts with the first request
    public record Rule(@Positive int limit, @NotNull Duration window) {
    }
}
