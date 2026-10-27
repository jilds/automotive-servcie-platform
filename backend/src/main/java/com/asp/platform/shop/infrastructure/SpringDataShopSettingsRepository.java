package com.asp.platform.shop.infrastructure;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

/// Row-level security shows the current workshop's row only, so the queries need no workshop id.
interface SpringDataShopSettingsRepository extends JpaRepository<ShopSettingsEntity, UUID> {

    @Query("select s from ShopSettingsEntity s")
    Optional<ShopSettingsEntity> findCurrent();

    /// Same lock timeout as `SpringDataProductRepository.lockById`: a request waits 3 s at most, then answers 409
    /// `try-again`.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from ShopSettingsEntity s")
    Optional<ShopSettingsEntity> lockCurrent();

    /// Inserts the workshop's settings row unless it has one; `tenant_id` comes from the connection. Two requests
    /// doing this at once insert one row: the second waits for the first and then does nothing.
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into shop_settings (id, hold_days, pickup_note, version, created_at, updated_at)
            values (:id, :holdDays, null, 0, now(), now())
            on conflict (tenant_id) do nothing""")
    int insertIfMissing(UUID id, int holdDays);
}
