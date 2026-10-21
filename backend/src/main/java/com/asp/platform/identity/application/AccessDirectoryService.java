package com.asp.platform.identity.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.asp.platform.identity.AccessDirectory;
import com.asp.platform.identity.UserAccess;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/// Looks up each signed-in user's access at most once per [#TTL], so checking every request costs one
/// primary-key read per user every 30 seconds. [IdentityService#changeAccess] evicts the entry, so a
/// change applies to the user's next request on this instance; other instances see it within the TTL.
@Service
public class AccessDirectoryService implements AccessDirectory {

    static final Duration TTL = Duration.ofSeconds(30);

    /// Bounds memory; far above the number of users active within one TTL.
    private static final long MAX_ENTRIES = 10_000;

    private final UserRepository users;
    private final Cache<UUID, UserAccess> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_ENTRIES)
            .build();

    AccessDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserAccess> access(UUID userId) {
        // Unknown users are not cached: the loader returns null, which Caffeine does not store.
        return Optional.ofNullable(cache.get(userId, id -> users.findById(id).map(AccessDirectoryService::access)
                .orElse(null)));
    }

    /// Forgets the user's cached access now and again when the current transaction ends, so a request
    /// running alongside the change cannot put the old value back.
    void evict(UUID userId) {
        cache.invalidate(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.invalidate(userId);
                }
            });
        }
    }

    private static UserAccess access(User user) {
        return new UserAccess(user.tenantId(), user.role(), user.active());
    }
}
