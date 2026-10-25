package com.asp.platform.booking.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.domain.BookedService;
import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingContact;
import com.asp.platform.booking.domain.BookingStatus;
import com.asp.platform.shared.persistence.DomainEntity;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security. The
/// contact, consents, token hash and services are written once, with the request.
@Entity
@Table(name = "booking")
class BookingEntity implements DomainEntity<Booking> {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "slot_start", nullable = false)
    private Instant slotStart;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(updatable = false)
    private @Nullable String phone;

    @Column(updatable = false)
    private @Nullable String email;

    @Column(nullable = false, updatable = false)
    private String plate;

    @Column(updatable = false)
    private @Nullable String note;

    @Column(name = "privacy_accepted_at", nullable = false, updatable = false)
    private Instant privacyAcceptedAt;

    @Column(name = "marketing_consent", nullable = false, updatable = false)
    private boolean marketingConsent;

    @Column(name = "status_token_hash", nullable = false, updatable = false)
    private String statusTokenHash;

    @Column(name = "vehicle_id")
    private @Nullable UUID vehicleId;

    @Column(name = "work_order_id")
    private @Nullable UUID workOrderId;

    @Column(name = "decided_by")
    private @Nullable UUID decidedBy;

    @Column(name = "decided_at")
    private @Nullable Instant decidedAt;

    @Column(name = "decline_reason")
    private @Nullable String declineReason;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<BookingServiceEntity> services = new ArrayList<>();

    @SuppressWarnings("NullAway.Init")
    protected BookingEntity() {
    }

    BookingEntity(Booking booking) {
        this.id = booking.id();
        this.number = booking.number();
        BookingContact c = booking.contact();
        this.name = c.name();
        this.phone = c.phone();
        this.email = c.email();
        this.plate = c.plate();
        this.note = c.note();
        this.privacyAcceptedAt = booking.privacyAcceptedAt();
        this.marketingConsent = booking.marketingConsent();
        this.statusTokenHash = booking.statusTokenHash();
        this.createdAt = booking.createdAt();
        List<BookedService> booked = booking.services();
        for (int i = 0; i < booked.size(); i++) {
            services.add(new BookingServiceEntity(this, i + 1, booked.get(i)));
        }
        apply(booking);
    }

    /// Copies what changes after the request: status, slot, decision and check-in.
    @Override
    public void apply(Booking booking) {
        this.status = booking.status();
        this.slotStart = booking.slotStart();
        this.vehicleId = booking.vehicleId();
        this.workOrderId = booking.workOrderId();
        this.decidedBy = booking.decidedBy();
        this.decidedAt = booking.decidedAt();
        this.declineReason = booking.declineReason();
    }

    @Override
    public Booking toDomain() {
        Long v = version;
        return new Booking(id, number, status, slotStart, new BookingContact(name, phone, email, plate, note),
                marketingConsent, privacyAcceptedAt, statusTokenHash,
                services.stream().map(BookingServiceEntity::toDomain).toList(), vehicleId, workOrderId, decidedBy,
                decidedAt, declineReason, createdAt, v == null ? 0 : v);
    }
}
