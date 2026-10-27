package com.asp.platform.shop.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderRepository;
import com.asp.platform.shop.domain.ShopOrderStatus;

@Repository
class JpaShopOrderRepository implements ShopOrderRepository {

    private static final Set<ShopOrderStatus> OPEN = Set.of(ShopOrderStatus.PLACED, ShopOrderStatus.READY);

    private final SpringDataShopOrderRepository jpa;

    JpaShopOrderRepository(SpringDataShopOrderRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ShopOrder save(ShopOrder order) {
        return Upserts.upsert(jpa, order.id(), order, ShopOrderEntity::new);
    }

    @Override
    public Optional<ShopOrder> findById(UUID id) {
        return jpa.findById(id).map(ShopOrderEntity::toDomain);
    }

    @Override
    public Optional<ShopOrder> findByTokenHash(String tokenHash) {
        return jpa.findByStatusTokenHash(tokenHash).map(ShopOrderEntity::toDomain);
    }

    @Override
    public List<ShopOrder> findPlacedBetween(Instant from, Instant to, @Nullable ShopOrderStatus status) {
        List<ShopOrderEntity> found = status == null ? jpa.findPlacedBetween(from, to)
                : jpa.findPlacedBetween(from, to, status);
        return found.stream().map(ShopOrderEntity::toDomain).toList();
    }

    @Override
    public List<UUID> findDueIds(Instant now, int limit) {
        return jpa.findDueIds(OPEN, now, Limit.of(limit));
    }

    @Override
    public long countOpen() {
        return jpa.countByStatusIn(OPEN);
    }

    @Override
    public long countByStatus(ShopOrderStatus status) {
        return jpa.countByStatus(status);
    }

    @Override
    public int nextNumber() {
        return jpa.nextNumber();
    }
}
