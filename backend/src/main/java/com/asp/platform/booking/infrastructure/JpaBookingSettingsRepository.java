package com.asp.platform.booking.infrastructure;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.BookingSettings;
import com.asp.platform.booking.domain.BookingSettingsRepository;
import com.asp.platform.booking.domain.OpeningRange;
import com.asp.platform.shared.persistence.Upserts;

@Repository
class JpaBookingSettingsRepository implements BookingSettingsRepository {

    /// Same as the row lock's timeout in [SpringDataBookingSettingsRepository#lockCurrent].
    static final String INSERT_LOCK_TIMEOUT = "3s";

    private final SpringDataBookingSettingsRepository jpa;
    private final EntityManager entityManager;

    JpaBookingSettingsRepository(SpringDataBookingSettingsRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<BookingSettings> find() {
        return jpa.findCurrent().map(BookingSettingsEntity::toDomain);
    }

    /// The row is created with SQL `on conflict do nothing`, not through the entity: a parallel insert would
    /// otherwise break the unique constraint and abort the whole transaction. That insert waits for a parallel
    /// first use that has not committed yet, so it runs under the same 3 s lock timeout as the row lock (then 409
    /// `try-again`); the transaction's own setting is restored after it. The settings may already be in this
    /// transaction's persistence context, so they are reloaded once the row is locked.
    @Override
    public BookingSettings lockOrCreate(BookingRules defaults) {
        UUID id = UUID.randomUUID();
        String previous = setLockTimeout(INSERT_LOCK_TIMEOUT);
        int inserted = jpa.insertIfMissing(id, defaults.slotMinutes(), defaults.carsPerSlot(),
                defaults.leadTimeHours(), defaults.horizonDays(), defaults.cancelUntilHours());
        setLockTimeout(previous);
        if (inserted == 1) {
            for (OpeningRange range : defaults.openingHours()) {
                jpa.insertOpeningRange(id, (short) range.weekday(), range.opens(), range.closes());
            }
        }
        BookingSettingsEntity entity = jpa.lockCurrent()
                .orElseThrow(() -> new IllegalStateException("No booking settings on this connection's workshop"));
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
    public BookingSettings save(BookingSettings settings) {
        return Upserts.upsert(jpa, settings.id(), settings, BookingSettingsEntity::new);
    }
}
