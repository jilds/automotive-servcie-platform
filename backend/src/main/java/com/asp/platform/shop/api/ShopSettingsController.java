package com.asp.platform.shop.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shop.application.ShopSettingsService;
import com.asp.platform.shop.domain.ShopSettings;

/// How the workshop runs its online shop: the front desk reads the settings, the owner changes them.
@RestController
@RequestMapping("/api/v1/shop/settings")
class ShopSettingsController {

    private final ShopSettingsService settings;

    ShopSettingsController(ShopSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    Response get() {
        return Response.of(settings.settings());
    }

    /// Needs the version read (0 before the first save); someone else's newer save answers 409 `stale-data`.
    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    Response update(@Valid @RequestBody Request request) {
        return Response.of(settings.update(request.requiredHoldDays(), request.pickupNote(),
                Versions.required(request.version())));
    }

    /// `PUT /shop/settings`: hold days 1 to 14, an optional pickup note of up to 500 characters, the version read.
    record Request(@NotNull @Min(ShopSettings.MIN_HOLD_DAYS) @Max(ShopSettings.MAX_HOLD_DAYS) @Nullable Integer holdDays,
            @Nullable @Size(max = ShopSettings.MAX_PICKUP_NOTE) String pickupNote, @NotNull @Nullable Long version) {

        int requiredHoldDays() {
            if (holdDays == null) {
                throw new IllegalStateException("holdDays is validated by @NotNull");
            }
            return holdDays;
        }
    }

    record Response(int holdDays, @Nullable String pickupNote, long version) {

        static Response of(ShopSettings s) {
            return new Response(s.holdDays(), s.pickupNote(), s.version());
        }
    }
}
