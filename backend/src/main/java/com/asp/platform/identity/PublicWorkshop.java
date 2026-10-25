package com.asp.platform.identity;

import java.time.ZoneId;
import java.util.UUID;

/// An open workshop as its public pages (`/api/v1/public/{slug}/...`) are served: no NIF, subscription, contact or
/// team data (ADR 0004).
///
/// @param name     the name to show: the display name when set, else the trading name
/// @param timeZone the workshop's time zone
/// @param country  ISO 3166-1 alpha-2 code, for example "PT"
/// @param booking  true when the platform admin switched on online booking
/// @param shop     true when the platform admin switched on the online shop
public record PublicWorkshop(UUID tenantId, String name, ZoneId timeZone, String country, boolean booking,
        boolean shop) {
}
