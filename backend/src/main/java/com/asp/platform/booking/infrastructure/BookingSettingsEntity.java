package com.asp.platform.booking.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.BookingSettings;
import com.asp.platform.booking.domain.Closure;
import com.asp.platform.booking.domain.OpeningRange;
import com.asp.platform.shared.persistence.DomainEntity;

/// `tenant_id` (also on the opening hours and closures) is filled by the database from the connection's workshop
/// and checked by row-level security. Opening hours and closures are value collections: a change rewrites them and
/// raises the settings' version.
@Entity
@Table(name = "booking_settings")
class BookingSettingsEntity implements DomainEntity<BookingSettings> {

    @Id
    private UUID id;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "cars_per_slot", nullable = false)
    private int carsPerSlot;

    @Column(name = "lead_time_hours", nullable = false)
    private int leadTimeHours;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "cancel_until_hours", nullable = false)
    private int cancelUntilHours;

    @ElementCollection
    @CollectionTable(name = "opening_hours", joinColumns = @JoinColumn(name = "settings_id"))
    private List<Hours> openingHours = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "booking_closure", joinColumns = @JoinColumn(name = "settings_id"))
    private List<Day> closures = new ArrayList<>();

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
    protected BookingSettingsEntity() {
    }

    BookingSettingsEntity(BookingSettings settings) {
        this.id = settings.id();
        apply(settings);
    }

    @Override
    public void apply(BookingSettings settings) {
        BookingRules r = settings.rules();
        this.slotMinutes = r.slotMinutes();
        this.carsPerSlot = r.carsPerSlot();
        this.leadTimeHours = r.leadTimeHours();
        this.horizonDays = r.horizonDays();
        this.cancelUntilHours = r.cancelUntilHours();
        List<Hours> hours = r.openingHours().stream().map(Hours::of).toList();
        if (!hours.equals(openingHours)) {
            openingHours.clear();
            openingHours.addAll(hours);
        }
        List<Day> days = r.closures().stream().map(Day::of).toList();
        if (!days.equals(closures)) {
            closures.clear();
            closures.addAll(days);
        }
    }

    @Override
    public BookingSettings toDomain() {
        Long v = version;
        return new BookingSettings(id, new BookingRules(slotMinutes, carsPerSlot, leadTimeHours, horizonDays,
                cancelUntilHours, openingHours.stream().map(Hours::toDomain).toList(),
                closures.stream().map(Day::toDomain).toList()), v == null ? 0 : v);
    }

    /// One row of `opening_hours`.
    @Embeddable
    static class Hours {

        @Column(nullable = false)
        private short weekday;

        @Column(nullable = false)
        private LocalTime opens;

        @Column(nullable = false)
        private LocalTime closes;

        @SuppressWarnings("NullAway.Init")
        protected Hours() {
        }

        private Hours(short weekday, LocalTime opens, LocalTime closes) {
            this.weekday = weekday;
            this.opens = opens;
            this.closes = closes;
        }

        static Hours of(OpeningRange range) {
            return new Hours((short) range.weekday(), range.opens(), range.closes());
        }

        OpeningRange toDomain() {
            return new OpeningRange(weekday, opens, closes);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Hours h && h.weekday == weekday && h.opens.equals(opens) && h.closes.equals(closes);
        }

        @Override
        public int hashCode() {
            return weekday * 31 * 31 + opens.hashCode() * 31 + closes.hashCode();
        }
    }

    /// One row of `booking_closure`.
    @Embeddable
    static class Day {

        @Column(nullable = false)
        private LocalDate day;

        private @Nullable String reason;

        @SuppressWarnings("NullAway.Init")
        protected Day() {
        }

        private Day(LocalDate day, @Nullable String reason) {
            this.day = day;
            this.reason = reason;
        }

        static Day of(Closure closure) {
            return new Day(closure.day(), closure.reason());
        }

        Closure toDomain() {
            return new Closure(day, reason);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Day d && d.day.equals(day) && java.util.Objects.equals(d.reason, reason);
        }

        @Override
        public int hashCode() {
            return day.hashCode() * 31 + java.util.Objects.hashCode(reason);
        }
    }
}
