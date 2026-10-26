package com.asp.platform.identity;

import java.time.ZoneId;

/// What another module may show about a workshop in a message to its customers or users.
///
/// @param name     the display name, else the workshop name
/// @param slug     its public pages and branded sign-in live under `/w/{slug}`
/// @param country  two-letter code, for the language of a message
/// @param timeZone for the times in a message
public record WorkshopProfile(String name, String slug, String country, ZoneId timeZone) {
}
