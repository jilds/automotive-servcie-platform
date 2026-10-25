package com.asp.platform.config.web;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

/// A fixed window per key, in this instance's memory: it starts with the key's first request and expires `window`
/// later. Used by the public pages' rate limits (ADR 0004).
final class FixedWindowLimit {

    /// Bounds memory; far above the number of clients active within one window.
    private static final long MAX_KEYS = 100_000;

    private final int max;
    private final long windowNanos;
    private final Ticker ticker;
    private final Cache<String, Window> windows;

    FixedWindowLimit(PublicRateLimitProperties.Rule rule, Ticker ticker) {
        this.max = rule.limit();
        this.windowNanos = rule.window().toNanos();
        this.ticker = ticker;
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(rule.window())
                .maximumSize(MAX_KEYS)
                .ticker(ticker)
                .build();
    }

    /// Counts one request.
    Outcome acquire(String key) {
        long now = ticker.read();
        Window window = windows.get(key, _ -> new Window(now, new AtomicInteger()));
        int count = window.count().incrementAndGet();
        if (count <= max) {
            return Outcome.ALLOWED;
        }
        long remainingNanos = Math.max(0, window.startNanos() + windowNanos - now);
        long seconds = TimeUnit.NANOSECONDS.toSeconds(remainingNanos + TimeUnit.SECONDS.toNanos(1) - 1);
        return new Outcome(Math.max(1, seconds), count == max + 1);
    }

    /// What [#acquire] decided.
    ///
    /// @param retryAfterSeconds 0 when the request is allowed, else the whole seconds until the window ends,
    ///                          rounded up (at least 1)
    /// @param firstRefusal      true only for the request that crossed the limit: the one refusal of the key and
    ///                          window worth logging, so a flood writes one log line, not one per request
    record Outcome(long retryAfterSeconds, boolean firstRefusal) {

        static final Outcome ALLOWED = new Outcome(0, false);

        boolean refused() {
            return retryAfterSeconds > 0;
        }
    }

    private record Window(long startNanos, AtomicInteger count) {
    }
}
