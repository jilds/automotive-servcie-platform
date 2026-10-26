package com.asp.platform.identity.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// `app.subscription.*`.
///
/// @param graceDays days after `subscription_until` during which the workshop may still sign in
@Validated
@ConfigurationProperties("app.subscription")
record SubscriptionProperties(@Min(0) @Max(365) int graceDays) {
}
