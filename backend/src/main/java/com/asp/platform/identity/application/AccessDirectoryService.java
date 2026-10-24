package com.asp.platform.identity.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.asp.platform.identity.AccessDirectory;
import com.asp.platform.identity.UserAccess;
import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.PlatformAdminRepository;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/// Looks up each signed-in user's access, their workshop's subscription status and platform admins at most once
/// per [#TTL], so checking every request costs one primary-key read per user and per workshop every 30 seconds.
/// [IdentityService#changeAccess] and [WorkshopAdminService#changeSubscription] evict the entry, so a change
/// applies to the next request on this instance; other instances see it within the TTL.
@Service
public class AccessDirectoryService implements AccessDirectory {

    static final Duration TTL = Duration.ofSeconds(30);

    /// Bounds memory; far above the number of users active within one TTL.
    private static final long MAX_ENTRIES = 10_000;

    private final UserRepository users;
    private final TenantRepository tenants;
    private final PlatformAdminRepository admins;
    private final Cache<UUID, UserAccess> userCache = cache();
    private final Cache<UUID, Boolean> workshopCache = cache();
    private final Cache<UUID, Boolean> adminCache = cache();

    AccessDirectoryService(UserRepository users, TenantRepository tenants, PlatformAdminRepository admins) {
        this.users = users;
        this.tenants = tenants;
        this.admins = admins;
    }

    @Override
    public Optional<UserAccess> access(UUID userId) {
        // Unknown users are not cached: the loader returns null, which Caffeine does not store.
        return Optional.ofNullable(userCache.get(userId, id -> users.findById(id)
                .map(AccessDirectoryService::access)
                .orElse(null)));
    }

    @Override
    public boolean workshopOpen(UUID tenantId) {
        Boolean open = workshopCache.get(tenantId, id -> tenants.findById(id).map(Tenant::allowsSignIn).orElse(null));
        return Boolean.TRUE.equals(open);
    }

    @Override
    public boolean isActivePlatformAdmin(UUID adminId) {
        Boolean active = adminCache.get(adminId, id -> admins.findById(id).map(PlatformAdmin::active).orElse(null));
        return Boolean.TRUE.equals(active);
    }

    /// Forgets the user's cached access now and again when the current transaction ends, so a request
    /// running alongside the change cannot put the old value back.
    void evict(UUID userId) {
        evictTwice(userCache, userId);
    }

    /// Same as [#evict] for a workshop whose subscription status changed.
    void evictWorkshop(UUID tenantId) {
        evictTwice(workshopCache, tenantId);
    }

    private static <V> void evictTwice(Cache<UUID, V> cache, UUID key) {
        cache.invalidate(key);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.invalidate(key);
                }
            });
        }
    }

    private static <V> Cache<UUID, V> cache() {
        return Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_ENTRIES).build();
    }

    private static UserAccess access(User user) {
        return new UserAccess(user.tenantId(), user.role(), user.active());
    }
}
