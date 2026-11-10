package com.asp.platform.billing.infrastructure;

import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/// Stops the start when the providers are set to something that cannot be right: an unknown name, or the sandbox in
/// production (its documents are not fiscal documents and its payments are not real).
public final class BillingProviderGuard {

    private static final Set<String> KNOWN = Set.of("sandbox", "none");
    static final int MIN_SECRET_BYTES = 32;

    private BillingProviderGuard() {
    }

    /// @param webhookSecret the secret that signs the payment provider webhooks; at least [#MIN_SECRET_BYTES] bytes
    ///                      whenever the payments provider is not none
    /// @throws IllegalStateException for an unknown provider, `sandbox` while the `prod` profile is active, or a
    ///                               missing or short webhook secret
    public static void check(Set<String> activeProfiles, String invoicingProvider, String paymentsProvider,
            String webhookSecret) {
        for (String provider : new String[] {invoicingProvider, paymentsProvider}) {
            if (!KNOWN.contains(provider)) {
                throw new IllegalStateException("Unknown billing provider '" + provider
                        + "': only sandbox and none exist until a real adapter is added");
            }
            if (activeProfiles.contains("prod") && "sandbox".equals(provider)) {
                throw new IllegalStateException(
                        "The billing sandbox must not run in production: set app.billing.invoicing.provider and "
                                + "app.billing.payments.provider to a real provider (or none)");
            }
        }
        if (!"none".equals(paymentsProvider)
                && webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("The webhook secret (app.billing.payments.webhook-secret, "
                    + "ASP_BILLING_WEBHOOK_SECRET) must be at least " + MIN_SECRET_BYTES
                    + " bytes while a payments provider is configured");
        }
    }

    /// Runs [#check] with the configuration when the application starts.
    @Component
    static class Startup {

        Startup(Environment environment, com.asp.platform.billing.application.BillingProperties properties) {
            check(Set.of(environment.getActiveProfiles()), properties.invoicing().provider(),
                    properties.payments().provider(), properties.payments().webhookSecret());
        }
    }
}
