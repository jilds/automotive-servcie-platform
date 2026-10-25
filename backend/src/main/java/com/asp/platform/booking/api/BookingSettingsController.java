package com.asp.platform.booking.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.booking.application.BookingSettingsService;
import com.asp.platform.shared.CurrentUser;

/// How the workshop takes bookings: the front desk reads them, the owner changes them.
@RestController
@RequestMapping("/api/v1/booking/settings")
class BookingSettingsController {

    private final BookingSettingsService settings;

    BookingSettingsController(BookingSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    BookingSettingsResponse get(CurrentUser user) {
        return BookingSettingsResponse.of(settings.settings(user.tenantId()));
    }

    /// Needs the version read (0 before the first save); someone else's newer save answers 409 `stale-data`.
    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    BookingSettingsResponse update(CurrentUser user, @Valid @RequestBody BookingSettingsRequest request) {
        return BookingSettingsResponse.of(settings.update(user.tenantId(), request.toRules(),
                request.requiredVersion()));
    }
}
