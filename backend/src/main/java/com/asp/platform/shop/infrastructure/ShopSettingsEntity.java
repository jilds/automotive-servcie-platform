package com.asp.platform.shop.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.shop.domain.ShopSettings;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "shop_settings")
class ShopSettingsEntity implements DomainEntity<ShopSettings> {

    @Id
    private UUID id;

    @Column(name = "hold_days", nullable = false)
    private int holdDays;

    @Column(name = "pickup_note")
    private @Nullable String pickupNote;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected ShopSettingsEntity() {
    }

    ShopSettingsEntity(ShopSettings settings) {
        this.id = settings.id();
        apply(settings);
    }

    @Override
    public void apply(ShopSettings settings) {
        this.holdDays = settings.holdDays();
        this.pickupNote = settings.pickupNote();
    }

    @Override
    public ShopSettings toDomain() {
        Long v = version;
        return new ShopSettings(id, holdDays, pickupNote, v == null ? 0 : v);
    }
}
