package com.asp.platform.booking.api;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.booking.application.BookingReceipt;
import com.asp.platform.booking.application.PublicBookingService;
import com.asp.platform.shared.PrivacyConsentRequiredException;
import com.asp.platform.shared.PublicWorkshopRef;
import com.asp.platform.shared.StatusTokens;

/// The booking pages of a workshop's public site (ADR 0004): anonymous, served for the workshop of the slug, 404
/// `add-on-not-available` while its booking add-on is off.
@RestController
@RequestMapping("/api/v1/public/{slug}/booking")
class PublicBookingController {

    private static final Logger log = LoggerFactory.getLogger(PublicBookingController.class);

    private final PublicBookingService booking;

    PublicBookingController(PublicBookingService booking) {
        this.booking = booking;
    }

    @GetMapping("/services")
    List<OnlineServiceResponse> services(PublicWorkshopRef workshop) {
        workshop.requireBooking();
        return booking.services().stream().map(OnlineServiceResponse::of).toList();
    }

    /// @param from first day, in the workshop's time zone; default today
    /// @param days 1 to 14, default 7; other values are brought into that range
    @GetMapping("/slots")
    List<SlotDayResponse> slots(PublicWorkshopRef workshop,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(defaultValue = "7") int days) {
        workshop.requireBooking();
        int span = Math.clamp(days, 1, PublicBookingService.MAX_DAYS);
        return booking.slots(workshop.timeZone(), from, span).stream().map(SlotDayResponse::of).toList();
    }

    /// A filled hidden `website` field is a bot: it is answered like a success and nothing is stored.
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    BookingReceiptResponse book(PublicWorkshopRef workshop, @Valid @RequestBody PublicBookingRequest request) {
        workshop.requireBooking();
        if (request.honeypotFilled()) {
            log.info("Public booking dropped: hidden field filled tenant={}", workshop.tenantId());
            return new BookingReceiptResponse(ThreadLocalRandom.current().nextInt(1, 1000), StatusTokens.generate(),
                    request.slotStart());
        }
        if (!request.acceptedPrivacy()) {
            throw new PrivacyConsentRequiredException();
        }
        BookingReceipt receipt = booking.book(workshop.tenantId(), workshop.timeZone(), request.toCommand());
        return BookingReceiptResponse.of(receipt);
    }

    @GetMapping("/bookings/{token}")
    PublicBookingResponse status(PublicWorkshopRef workshop, @PathVariable String token) {
        workshop.requireBooking();
        return PublicBookingResponse.of(booking.status(token, workshop.name(), workshop.timeZone()));
    }

    /// 409 `booking-cancel-too-late` after the workshop's deadline.
    @PostMapping("/bookings/{token}/cancel")
    PublicBookingResponse cancel(PublicWorkshopRef workshop, @PathVariable String token) {
        workshop.requireBooking();
        return PublicBookingResponse.of(booking.cancel(token, workshop.name(), workshop.timeZone()));
    }
}
