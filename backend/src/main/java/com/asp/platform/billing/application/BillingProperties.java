package com.asp.platform.billing.application;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// `app.billing.*`.
///
/// @param invoicing                the invoicing provider: `sandbox` or `none`
/// @param payments                 the payment provider and its webhook secret (from the environment only)
/// @param finalConsumerLimitCents  the largest total, in cents, a document may have without the customer NIF and address
/// @param multibancoValidDays      days a Multibanco reference stays payable
/// @param mbwayValidMinutes        minutes an MB WAY request stays open
/// @param retry                    the retry of documents the provider has not answered
/// @param providerTimeout          the longest a provider call may take (HTTP connect and read timeouts of a real
///                                 adapter): keep it well below the 3 s row lock timeout, because a void and a
///                                 payment creation call the provider while holding the invoice lock. A discard also
///                                 waits this long plus 30 s for an attempt that has not stored its result
@Validated
@ConfigurationProperties("app.billing")
public record BillingProperties(
        @DefaultValue Invoicing invoicing,
        @DefaultValue Payments payments,
        @DefaultValue("100000") @Min(0) long finalConsumerLimitCents,
        @DefaultValue("3") @Min(1) int multibancoValidDays,
        @DefaultValue("5") @Min(1) int mbwayValidMinutes,
        @DefaultValue Retry retry,
        @DefaultValue("2s") Duration providerTimeout) {

    /// A void and a payment creation call the provider holding the invoice row lock (3 s lock timeout): the provider
    /// timeout must leave room, so it is positive and at most 2.5 s.
    @AssertTrue(message = "app.billing.provider-timeout must be above 0 and at most 2.5 s")
    public boolean isProviderTimeoutBounded() {
        return providerTimeout.compareTo(Duration.ZERO) > 0 && providerTimeout.compareTo(Duration.ofMillis(2500)) <= 0;
    }

    public record Invoicing(@DefaultValue("sandbox") String provider) {
    }

    /// The secret is never logged; the record does not print it.
    public record Payments(@DefaultValue("sandbox") String provider, @DefaultValue("") String webhookSecret) {

        @Override
        public String toString() {
            return "Payments[provider=" + provider + "]";
        }
    }

    /// @param maxAttempts attempts (the first included) after which only staff retry
    /// @param minAge      time after the last attempt before the job tries again
    public record Retry(@DefaultValue("5") @Min(1) int maxAttempts, @DefaultValue("1m") Duration minAge) {
    }
}
