package com.asp.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.asp.platform.identity.domain.Plan;
import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.Subscription;
import com.asp.platform.identity.domain.SubscriptionStatus;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.WorkshopSettings;

/// `subscription_until` plus the grace days, read on the workshop's own calendar day.
class SubscriptionPolicyTests {

    /// 2026-10-26 00:30 in Lisbon (WET, UTC+0) and 2026-10-25 21:30 in São Paulo (UTC-3).
    private static final Instant NOW = Instant.parse("2026-10-26T00:30:00Z");

    private final SubscriptionPolicy policy = new SubscriptionPolicy(Clock.fixed(NOW, ZoneOffset.UTC),
            new SubscriptionProperties(7));

    @Test
    void should_allow_sign_in_until_the_last_grace_day_and_refuse_the_day_after() {
        assertThat(policy.allowsSignIn(lisbon(SubscriptionStatus.ACTIVE, LocalDate.of(2026, 10, 19))))
                .as("26 Oct is the 7th grace day").isTrue();
        Tenant expired = lisbon(SubscriptionStatus.ACTIVE, LocalDate.of(2026, 10, 18));
        assertThat(policy.allowsSignIn(expired)).isFalse();
        assertThat(policy.expired(expired)).isTrue();
    }

    @Test
    void should_never_expire_a_subscription_without_an_end_date() {
        Tenant open = lisbon(SubscriptionStatus.TRIAL, null);

        assertThat(policy.allowsSignIn(open)).isTrue();
        assertThat(policy.expired(open)).isFalse();
    }

    @Test
    void should_keep_refusing_suspended_and_cancelled_workshops_whatever_the_date() {
        assertThat(policy.allowsSignIn(lisbon(SubscriptionStatus.SUSPENDED, null))).isFalse();
        assertThat(policy.allowsSignIn(lisbon(SubscriptionStatus.CANCELLED, LocalDate.of(2027, 1, 1)))).isFalse();
    }

    @Test
    void should_read_the_day_in_the_workshops_time_zone() {
        LocalDate until = LocalDate.of(2026, 10, 18);
        Tenant saoPaulo = tenant(SubscriptionStatus.ACTIVE, until, "BR", "America/Sao_Paulo");

        // Still 25 Oct in São Paulo: the 7th grace day.
        assertThat(policy.allowsSignIn(saoPaulo)).isTrue();
        assertThat(policy.allowsSignIn(lisbon(SubscriptionStatus.ACTIVE, until))).isFalse();
    }

    @Test
    void should_lock_out_the_day_after_the_end_date_without_grace_days() {
        SubscriptionPolicy strict = new SubscriptionPolicy(Clock.fixed(NOW, ZoneOffset.UTC),
                new SubscriptionProperties(0));

        assertThat(strict.allowsSignIn(lisbon(SubscriptionStatus.ACTIVE, LocalDate.of(2026, 10, 26)))).isTrue();
        assertThat(strict.allowsSignIn(lisbon(SubscriptionStatus.ACTIVE, LocalDate.of(2026, 10, 25)))).isFalse();
    }

    private static Tenant lisbon(SubscriptionStatus status, @Nullable LocalDate until) {
        return tenant(status, until, "PT", "Europe/Lisbon");
    }

    private static Tenant tenant(SubscriptionStatus status, @Nullable LocalDate until, String country, String zone) {
        return Tenant.provision(UUID.randomUUID(), WorkshopSettings.initial("Oficina", null, country, zone),
                new Subscription(Plan.PRO, status, until), new Slug("oficina"), Instant.EPOCH);
    }
}
