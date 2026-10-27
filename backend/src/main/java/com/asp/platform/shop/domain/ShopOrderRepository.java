package com.asp.platform.shop.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Row-level security limits every query to the current workshop.
public interface ShopOrderRepository {

    /// Flushes, so the returned order carries the stored version.
    ShopOrder save(ShopOrder order);

    Optional<ShopOrder> findById(UUID id);

    /// @param tokenHash [com.asp.platform.shared.StatusTokens#hash] of the status link's token
    Optional<ShopOrder> findByTokenHash(String tokenHash);

    /// Orders placed in [from, to), newest first; any status when null.
    List<ShopOrder> findPlacedBetween(Instant from, Instant to, @Nullable ShopOrderStatus status);

    /// Ids of open orders (PLACED, READY) whose hold ended before `now`, earliest hold first, at most `limit`.
    List<UUID> findDueIds(Instant now, int limit);

    long countByStatus(ShopOrderStatus status);

    /// Open orders (PLACED, READY) of the workshop.
    long countOpen();

    /// The next order number of the workshop. Call it while holding the settings lock, which serialises new orders;
    /// the unique (tenant, number) constraint backs it up.
    int nextNumber();
}
