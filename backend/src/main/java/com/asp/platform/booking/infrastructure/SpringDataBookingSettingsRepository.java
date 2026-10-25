package com.asp.platform.booking.infrastructure;

import java.time.LocalTime;
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
interface SpringDataBookingSettingsRepository extends JpaRepository<BookingSettingsEntity, UUID> {

    @Query("select s from BookingSettingsEntity s")
    Optional<BookingSettingsEntity> findCurrent();

    /// Same lock timeout as `SpringDataProductRepository.lockById`: a request waits 3 s at most, then answers 409
    /// `try-again`.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from BookingSettingsEntity s")
    Optional<BookingSettingsEntity> lockCurrent();

    /// Inserts the workshop's settings row unless it has one; `tenant_id` comes from the connection. Two requests
    /// doing this at once insert one row: the second waits for the first and then does nothing.
    ///
    /// @return 1 when the row was inserted, 0 when it existed
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into booking_settings (id, slot_minutes, cars_per_slot, lead_time_hours, horizon_days,
                                          cancel_until_hours, version, created_at, updated_at)
            values (:id, :slotMinutes, :carsPerSlot, :leadTimeHours, :horizonDays, :cancelUntilHours, 0, now(), now())
            on conflict (tenant_id) do nothing""")
    int insertIfMissing(UUID id, int slotMinutes, int carsPerSlot, int leadTimeHours, int horizonDays,
            int cancelUntilHours);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into opening_hours (settings_id, weekday, opens, closes)
            values (:settingsId, :weekday, :opens, :closes)""")
    void insertOpeningRange(UUID settingsId, short weekday, LocalTime opens, LocalTime closes);
}
