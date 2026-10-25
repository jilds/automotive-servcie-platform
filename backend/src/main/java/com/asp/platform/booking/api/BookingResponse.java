package com.asp.platform.booking.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.application.BookingView;
import com.asp.platform.booking.domain.BookedService;
import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingContact;
import com.asp.platform.booking.domain.BookingStatus;

/// A booking as the front desk sees it. `vehicleId` is the workshop's vehicle with the booked plate, when there is
/// one (the checked-in vehicle once checked in). Prices of the services are the ones shown when it was booked.
record BookingResponse(UUID id, int number, BookingStatus status, Instant slotStart, String name,
        @Nullable String phone, @Nullable String email, String plate, @Nullable String note,
        boolean marketingConsent, Instant createdAt, List<Service> services, @Nullable UUID vehicleId,
        @Nullable UUID workOrderId, @Nullable String decidedByName, @Nullable Instant decidedAt,
        @Nullable String declineReason, long version) {

    record Service(UUID serviceId, String name, int durationMinutes, long priceCents, BigDecimal vatRate) {

        static Service of(BookedService s) {
            return new Service(s.serviceItemId(), s.name(), s.durationMinutes(), s.priceCents(), s.vatRate());
        }
    }

    static BookingResponse of(BookingView view) {
        Booking b = view.booking();
        BookingContact c = b.contact();
        return new BookingResponse(b.id(), b.number(), b.status(), b.slotStart(), c.name(), c.phone(), c.email(),
                c.plate(), c.note(), b.marketingConsent(), b.createdAt(),
                b.services().stream().map(Service::of).toList(), view.vehicleId(), b.workOrderId(),
                view.decidedByName(), b.decidedAt(), b.declineReason(), b.version());
    }
}
