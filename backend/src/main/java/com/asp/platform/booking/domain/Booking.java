package com.asp.platform.booking.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// A drop-off booking requested on the workshop's public page.
///
/// Status moves: REQUESTED → CONFIRMED | DECLINED | CANCELLED; CONFIRMED → CHECKED_IN | CANCELLED | NO_SHOW. Moving
/// to another slot keeps (or makes) it CONFIRMED. The customer may cancel a booking that holds a place until
/// `cancelUntilHours` before its slot.
public final class Booking {

    public static final int MAX_DECLINE_REASON = 500;

    private final UUID id;
    private final int number;
    private final BookingContact contact;
    private final boolean marketingConsent;
    private final Instant privacyAcceptedAt;
    private final String statusTokenHash;
    private final List<BookedService> services;
    private final Instant createdAt;
    private final long version;
    private BookingStatus status;
    private Instant slotStart;
    private @Nullable UUID vehicleId;
    private @Nullable UUID workOrderId;
    private @Nullable UUID decidedBy;
    private @Nullable Instant decidedAt;
    private @Nullable String declineReason;

    /// A booking as stored.
    public Booking(UUID id, int number, BookingStatus status, Instant slotStart, BookingContact contact,
            boolean marketingConsent, Instant privacyAcceptedAt, String statusTokenHash, List<BookedService> services,
            @Nullable UUID vehicleId, @Nullable UUID workOrderId, @Nullable UUID decidedBy,
            @Nullable Instant decidedAt, @Nullable String declineReason, Instant createdAt, long version) {
        if (services.isEmpty()) {
            throw new IllegalArgumentException("A booking has at least one service");
        }
        this.id = id;
        this.number = number;
        this.status = status;
        this.slotStart = slotStart;
        this.contact = contact;
        this.marketingConsent = marketingConsent;
        this.privacyAcceptedAt = privacyAcceptedAt;
        this.statusTokenHash = statusTokenHash;
        this.services = List.copyOf(services);
        this.vehicleId = vehicleId;
        this.workOrderId = workOrderId;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.declineReason = declineReason;
        this.createdAt = createdAt;
        this.version = version;
    }

    /// A new request from the public page, not saved yet. The customer accepted the privacy notice `now`.
    public static Booking request(UUID id, int number, Instant slotStart, BookingContact contact,
            List<BookedService> services, boolean marketingConsent, String statusTokenHash, Instant now) {
        return new Booking(id, number, BookingStatus.REQUESTED, slotStart, contact, marketingConsent, now,
                statusTokenHash, services, null, null, null, null, null, now, 0);
    }

    /// @throws StaleDataException when someone else saved the booking since the caller read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    /// REQUESTED → CONFIRMED.
    public void confirm(UUID userId, Instant at) {
        require(status == BookingStatus.REQUESTED);
        status = BookingStatus.CONFIRMED;
        decided(userId, at);
    }

    /// REQUESTED → DECLINED, with the reason the customer sees on the status link.
    ///
    /// @throws DeclineReasonRequiredException when the reason is blank
    public void decline(@Nullable String reason, UUID userId, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new DeclineReasonRequiredException();
        }
        String r = reason.strip();
        if (r.length() > MAX_DECLINE_REASON) {
            throw new IllegalArgumentException("Reason is longer than " + MAX_DECLINE_REASON + " characters");
        }
        require(status == BookingStatus.REQUESTED);
        status = BookingStatus.DECLINED;
        declineReason = r;
        decided(userId, at);
    }

    /// A new time proposed by the front desk: REQUESTED or CONFIRMED → CONFIRMED at `newStart`. The caller checks
    /// that the slot is free.
    public void moveTo(Instant newStart, UUID userId, Instant at) {
        require(status.holdsAPlace());
        slotStart = newStart;
        status = BookingStatus.CONFIRMED;
        decided(userId, at);
    }

    /// CONFIRMED → NO_SHOW, once the slot has started.
    public void noShow(UUID userId, Instant now) {
        require(status == BookingStatus.CONFIRMED && slotStart.isBefore(now));
        status = BookingStatus.NO_SHOW;
        decided(userId, now);
    }

    /// The customer cancels on the status link.
    ///
    /// @throws IllegalBookingStatusException  when the booking no longer holds a place
    /// @throws BookingCancelTooLateException when the deadline has passed
    public void cancelByCustomer(Instant now, int cancelUntilHours) {
        require(status.holdsAPlace());
        if (!canCancel(now, cancelUntilHours)) {
            throw new BookingCancelTooLateException();
        }
        status = BookingStatus.CANCELLED;
    }

    /// True while the customer may still cancel online.
    public boolean canCancel(Instant now, int cancelUntilHours) {
        return status.holdsAPlace() && !now.isAfter(slotStart.minus(Duration.ofHours(cancelUntilHours)));
    }

    /// The front desk starts a check-in from this booking for the vehicle chosen at the counter.
    ///
    /// @throws BookingNotConfirmedException unless the booking is CONFIRMED
    public void claimFor(UUID vehicle) {
        requireConfirmed();
        vehicleId = vehicle;
    }

    /// CONFIRMED → CHECKED_IN, with the work order it opened.
    ///
    /// @throws BookingNotConfirmedException unless the booking is CONFIRMED
    public void checkedIn(UUID workOrder) {
        requireConfirmed();
        status = BookingStatus.CHECKED_IN;
        workOrderId = workOrder;
    }

    private void requireConfirmed() {
        if (status != BookingStatus.CONFIRMED) {
            throw new BookingNotConfirmedException();
        }
    }

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new IllegalBookingStatusException();
        }
    }

    private void decided(UUID userId, Instant at) {
        decidedBy = userId;
        decidedAt = at;
    }

    public UUID id() {
        return id;
    }

    public int number() {
        return number;
    }

    public BookingStatus status() {
        return status;
    }

    public Instant slotStart() {
        return slotStart;
    }

    public BookingContact contact() {
        return contact;
    }

    public boolean marketingConsent() {
        return marketingConsent;
    }

    public Instant privacyAcceptedAt() {
        return privacyAcceptedAt;
    }

    public String statusTokenHash() {
        return statusTokenHash;
    }

    public List<BookedService> services() {
        return services;
    }

    public @Nullable UUID vehicleId() {
        return vehicleId;
    }

    public @Nullable UUID workOrderId() {
        return workOrderId;
    }

    public @Nullable UUID decidedBy() {
        return decidedBy;
    }

    public @Nullable Instant decidedAt() {
        return decidedAt;
    }

    public @Nullable String declineReason() {
        return declineReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }
}
