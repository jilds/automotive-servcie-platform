package com.asp.platform.shared;

import java.time.ZoneId;
import java.util.UUID;

/// The workshop a public page request (`/api/v1/public/{slug}/...`) is served for, found by its slug (ADR 0004).
/// Controllers under that path take it as a method parameter; the request already runs inside its [TenantScope].
/// Only open workshops get here: an unknown slug or a suspended or cancelled workshop answers 404 before.
///
/// @param name     the name to show: the display name when set, else the trading name
/// @param timeZone the workshop's time zone, in which opening hours and dates are read
/// @param country  ISO 3166-1 alpha-2 code, for example "PT"
/// @param booking  true when the online booking add-on is on
/// @param shop     true when the online shop add-on is on
public record PublicWorkshopRef(UUID tenantId, String name, ZoneId timeZone, String country, boolean booking,
        boolean shop) {

    /// @throws AddOnNotAvailableException when online booking is off for this workshop
    public void requireBooking() {
        if (!booking) {
            throw new AddOnNotAvailableException();
        }
    }

    /// @throws AddOnNotAvailableException when the online shop is off for this workshop
    public void requireShop() {
        if (!shop) {
            throw new AddOnNotAvailableException();
        }
    }
}
