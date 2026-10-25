package com.asp.platform.booking.api;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.application.PublicBookingView;
import com.asp.platform.booking.domain.BookedService;
import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingStatus;

/// A booking as its status link shows it: no contact data, no internal ids (the token is the only key).
/// `timeZone` is the workshop's IANA zone id, to show `slotStart` in local time.
record PublicBookingResponse(int number, BookingStatus status, Instant slotStart, String workshopName, String timeZone,
        List<Service> services, boolean canCancel, @Nullable String declineReason) {

    record Service(String name, int durationMinutes, long priceWithVatCents) {

        static Service of(BookedService s) {
            return new Service(s.name(), s.durationMinutes(), s.priceWithVatCents());
        }
    }

    static PublicBookingResponse of(PublicBookingView view) {
        Booking b = view.booking();
        return new PublicBookingResponse(b.number(), b.status(), b.slotStart(), view.workshopName(),
                view.timeZone().getId(),
                b.services().stream().map(Service::of).toList(), view.canCancel(), b.declineReason());
    }
}
