package com.asp.platform.shop.infrastructure;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.asp.platform.shared.persistence.Upserts;
import com.asp.platform.shop.domain.ShopSettings;
import com.asp.platform.shop.domain.ShopSettingsRepository;

@Repository
class JpaShopSettingsRepository implements ShopSettingsRepository {

    /// Same as the row lock's timeout in [SpringDataShopSettingsRepository#lockCurrent].
    static final String INSERT_LOCK_TIMEOUT = "3s";

    private final SpringDataShopSettingsRepository jpa;
    private final EntityManager entityManager;

    JpaShopSettingsRepository(SpringDataShopSettingsRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ShopSettings> find() {
        return jpa.findCurrent().map(ShopSettingsEntity::toDomain);
    }

    /// Like the booking settings: the row is created with SQL `on conflict do nothing` (a parallel insert through
    /// the entity would break the unique constraint and abort the transaction), under a 3 s lock timeout, then
    /// locked and reloaded, since it may already be in this transaction's persistence context.
    @Override
    public ShopSettings lockOrCreate() {
        String previous = setLockTimeout(INSERT_LOCK_TIMEOUT);
        jpa.insertIfMissing(UUID.randomUUID(), ShopSettings.DEFAULT_HOLD_DAYS);
        setLockTimeout(previous);
        ShopSettingsEntity entity = jpa.lockCurrent()
                .orElseThrow(() -> new IllegalStateException("No shop settings on this connection's workshop"));
        entityManager.refresh(entity);
        return entity.toDomain();
    }

    /// Sets `lock_timeout` for the rest of the transaction (`set_config(..., true)` is `set local`).
    ///
    /// @return the value it had before
    private String setLockTimeout(String value) {
        Object before = entityManager.createNativeQuery("select current_setting('lock_timeout')").getSingleResult();
        entityManager.createNativeQuery("select set_config('lock_timeout', :value, true)")
                .setParameter("value", value)
                .getSingleResult();
        return String.valueOf(before);
    }

    @Override
    public ShopSettings save(ShopSettings settings) {
        return Upserts.upsert(jpa, settings.id(), settings, ShopSettingsEntity::new);
    }
}
