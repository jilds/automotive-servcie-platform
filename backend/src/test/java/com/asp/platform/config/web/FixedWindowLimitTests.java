package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Ticker;

/// The public pages' fixed windows (ADR 0004), on a fake clock.
class FixedWindowLimitTests {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final Ticker ticker = nanos::get;
    private final FixedWindowLimit limit =
            new FixedWindowLimit(new PublicRateLimitProperties.Rule(2, Duration.ofMinutes(1)), ticker);

    @Test
    void should_allow_the_limit_and_then_ask_to_wait_for_the_rest_of_the_window() {
        assertThat(limit.acquire("a").retryAfterSeconds()).isZero();
        assertThat(limit.acquire("a").retryAfterSeconds()).isZero();
        assertThat(limit.acquire("a").retryAfterSeconds()).isEqualTo(60);
        // Other keys have their own window.
        assertThat(limit.acquire("b").retryAfterSeconds()).isZero();
    }

    @Test
    void should_round_retry_after_up_to_whole_seconds_and_never_below_one() {
        limit.acquire("a");
        limit.acquire("a");

        advance(Duration.ofMillis(29_800));
        assertThat(limit.acquire("a").retryAfterSeconds()).isEqualTo(31);

        advance(Duration.ofMillis(30_100));
        assertThat(limit.acquire("a").retryAfterSeconds()).isEqualTo(1);

        advance(Duration.ofMillis(99));
        assertThat(limit.acquire("a").retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void should_start_a_new_window_once_the_old_one_ended() {
        limit.acquire("a");
        limit.acquire("a");
        assertThat(limit.acquire("a").retryAfterSeconds()).isPositive();

        advance(Duration.ofSeconds(60).plusMillis(1));

        assertThat(limit.acquire("a").retryAfterSeconds()).isZero();
        assertThat(limit.acquire("a").retryAfterSeconds()).isZero();
        assertThat(limit.acquire("a").retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void should_keep_the_window_start_of_the_first_request() {
        limit.acquire("a");
        advance(Duration.ofSeconds(50));
        limit.acquire("a");

        // The window started 50 s ago, not with the second request.
        assertThat(limit.acquire("a").retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    void should_report_only_the_request_that_crossed_the_limit_as_the_first_refusal() {
        assertThat(limit.acquire("a")).isEqualTo(FixedWindowLimit.Outcome.ALLOWED);
        assertThat(limit.acquire("a").firstRefusal()).isFalse();

        FixedWindowLimit.Outcome crossing = limit.acquire("a");
        assertThat(crossing.refused()).isTrue();
        assertThat(crossing.firstRefusal()).isTrue();
        // Every later refusal of the same window stays quiet, also under a flood.
        for (int i = 0; i < 50; i++) {
            FixedWindowLimit.Outcome later = limit.acquire("a");
            assertThat(later.refused()).isTrue();
            assertThat(later.firstRefusal()).isFalse();
        }
        // Another key crosses its own limit once.
        limit.acquire("b");
        limit.acquire("b");
        assertThat(limit.acquire("b").firstRefusal()).isTrue();

        // A new window: the next crossing is reported again.
        advance(Duration.ofSeconds(61));
        limit.acquire("a");
        limit.acquire("a");
        assertThat(limit.acquire("a").firstRefusal()).isTrue();
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
