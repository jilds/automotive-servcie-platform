package com.asp.platform.identity.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.asp.platform.identity.PublicWorkshop;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/// The public pages' slug lookups (ADR 0004), misses included, for at most [#TTL]: a flood of one unknown slug costs
/// one tenant query per TTL. Every change to what a lookup returns evicts it, so a suspended workshop answers 404 at
/// the next request on this instance; other instances see it within the TTL.
///
/// Evicted now and again when the current transaction ends, like [AccessDirectoryService], so a lookup running
/// alongside the change cannot put the old value back.
@Component
class PublicWorkshopCache {

    static final Duration TTL = Duration.ofSeconds(30);

    /// Bounds memory, misses included.
    private static final long MAX_ENTRIES = 10_000;

    private final Cache<String, Optional<PublicWorkshop>> bySlug = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_ENTRIES)
            .build();

    Optional<PublicWorkshop> get(String slug, Function<String, Optional<PublicWorkshop>> loader) {
        return bySlug.get(slug, loader);
    }

    /// After a change to the workshop's subscription, add-ons, slug, name, country or time zone: forgets its entry,
    /// whichever slug it was cached under, and the miss cached under `slug` while it was closed (a miss carries no
    /// tenant id).
    ///
    /// @param slug the workshop's slug before the change
    void evictWorkshop(UUID tenantId, String slug) {
        evictTwice(() -> {
            bySlug.invalidate(slug);
            bySlug.asMap().values()
                    .removeIf(cached -> cached.map(w -> w.tenantId().equals(tenantId)).orElse(false));
        });
    }

    /// After a workshop took a slug: forgets a miss cached for it.
    void evictSlug(String slug) {
        evictTwice(() -> bySlug.invalidate(slug));
    }

    private static void evictTwice(Runnable evict) {
        evict.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    evict.run();
                }
            });
        }
    }
}
