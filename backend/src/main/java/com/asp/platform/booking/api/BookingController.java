package com.asp.platform.booking.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.booking.application.BookingService;
import com.asp.platform.booking.application.PublicBookingService;
import com.asp.platform.booking.domain.BookingStatus;
import com.asp.platform.shared.CurrentUser;

/// The front desk's bookings. Owner and front desk only: bookings carry the customer's contact data, which
/// mechanics never get. Every decision sends the version it read (409 `stale-data`).
@RestController
@RequestMapping("/api/v1/bookings")
class BookingController {

    private final BookingService bookings;

    BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    /// @param from first day (workshop's time zone), default today
    /// @param to   last day, included, default `from` + 7 days; at most 124 days after `from` (400 `validation`)
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<BookingResponse> list(CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable BookingStatus status) {
        return bookings.list(user.tenantId(), from, to, status).stream().map(BookingResponse::of).toList();
    }

    /// Free slots to move a booking to, in the public slots' shape: future slots of the opening hours on open
    /// days with a place left, whatever the lead time and horizon. Not under the public rate limit.
    ///
    /// @param from first day (workshop's time zone), default today
    /// @param days 1 to 14, default 7; other values are brought into that range
    @GetMapping("/slots")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<SlotDayResponse> slots(CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(defaultValue = "7") int days) {
        int span = Math.clamp(days, 1, PublicBookingService.MAX_DAYS);
        return bookings.slots(user.tenantId(), from, span).stream().map(SlotDayResponse::of).toList();
    }

    /// `{ "count": 3 }`: bookings waiting for a decision.
    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    Map<String, Long> pendingCount() {
        return Map.of("count", bookings.pendingCount());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    BookingResponse get(CurrentUser user, @PathVariable UUID id) {
        return BookingResponse.of(bookings.get(user.tenantId(), id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    BookingResponse confirm(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return BookingResponse.of(bookings.confirm(user.tenantId(), user.userId(), id, request.requiredVersion()));
    }

    /// 400 `decline-reason-required` without a reason.
    @PostMapping("/{id}/decline")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    BookingResponse decline(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody DeclineRequest request) {
        return BookingResponse.of(bookings.decline(user.tenantId(), user.userId(), id, request.reason(),
                request.requiredVersion()));
    }

    /// To a free slot of the opening hours that has not started; the booking becomes CONFIRMED. 409
    /// `slot-unavailable` otherwise.
    @PostMapping("/{id}/move")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    BookingResponse move(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody MoveRequest request) {
        return BookingResponse.of(bookings.move(user.tenantId(), user.userId(), id, request.slotStart(),
                request.requiredVersion()));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    BookingResponse noShow(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return BookingResponse.of(bookings.noShow(user.tenantId(), user.userId(), id, request.requiredVersion()));
    }
}
