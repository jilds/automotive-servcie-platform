package com.asp.platform.shop.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.asp.platform.shop.domain.ShopOrderStatus;

/// Row-level security limits every query to the current workshop.
interface SpringDataShopOrderRepository extends JpaRepository<ShopOrderEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<ShopOrderEntity> findById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<ShopOrderEntity> findByStatusTokenHash(String statusTokenHash);

    @EntityGraph(attributePaths = "lines")
    @Query("""
            select o from ShopOrderEntity o
            where o.placedAt >= :from and o.placedAt < :to
            order by o.placedAt desc, o.number desc""")
    List<ShopOrderEntity> findPlacedBetween(Instant from, Instant to);

    @EntityGraph(attributePaths = "lines")
    @Query("""
            select o from ShopOrderEntity o
            where o.placedAt >= :from and o.placedAt < :to and o.status = :status
            order by o.placedAt desc, o.number desc""")
    List<ShopOrderEntity> findPlacedBetween(Instant from, Instant to, ShopOrderStatus status);

    @Query("""
            select o.id from ShopOrderEntity o
            where o.status in :statuses and o.holdUntil < :now
            order by o.holdUntil, o.number""")
    List<UUID> findDueIds(Collection<ShopOrderStatus> statuses, Instant now, Limit limit);

    long countByStatus(ShopOrderStatus status);

    long countByStatusIn(Collection<ShopOrderStatus> statuses);

    /// Row-level security limits the max to the current workshop.
    @Query("select coalesce(max(o.number), 0) + 1 from ShopOrderEntity o")
    int nextNumber();
}
