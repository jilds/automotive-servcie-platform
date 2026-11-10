package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.asp.platform.billing.infrastructure.BillingProviderGuard;

/// What the application refuses to start with: the sandbox in production, an unknown provider name, and a payments
/// provider without a webhook secret of at least 32 bytes.
class BillingProviderGuardTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void should_refuse_the_sandbox_in_production() {
        assertThatThrownBy(() -> BillingProviderGuard.check(Set.of("prod"), "sandbox", "none", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sandbox");
        assertThatThrownBy(() -> BillingProviderGuard.check(Set.of("prod", "x"), "none", "sandbox", SECRET))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sandbox");
    }

    @Test
    void should_refuse_an_unknown_provider() {
        assertThatThrownBy(() -> BillingProviderGuard.check(Set.of(), "moloni", "none", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("moloni");
        assertThatThrownBy(() -> BillingProviderGuard.check(Set.of("dev"), "sandbox", "ifthenpay", SECRET))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ifthenpay");
    }

    @Test
    void should_accept_none_and_the_sandbox_outside_production() {
        assertThatCode(() -> BillingProviderGuard.check(Set.of("prod"), "none", "none", "")).doesNotThrowAnyException();
        assertThatCode(() -> BillingProviderGuard.check(Set.of("dev"), "sandbox", "sandbox", SECRET))
                .doesNotThrowAnyException();
        assertThatCode(() -> BillingProviderGuard.check(Set.of(), "sandbox", "none", "short"))
                .doesNotThrowAnyException();
    }

    @Test
    void should_require_a_long_webhook_secret_when_payments_are_configured() {
        assertThatThrownBy(() -> BillingProviderGuard.check(Set.of("dev"), "sandbox", "sandbox", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("webhook secret");
        assertThatThrownBy(() -> BillingProviderGuard.check(Set.of("dev"), "none", "sandbox", SECRET.substring(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("webhook secret");
        assertThatCode(() -> BillingProviderGuard.check(Set.of("dev"), "none", "sandbox", SECRET))
                .doesNotThrowAnyException();
    }
}
