package com.asp.platform.identity;

import org.jspecify.annotations.Nullable;

/// What a workshop prints on the invoices it issues. The postal address is not part of the settings yet.
///
/// @param name    the workshop's trading name
/// @param nif     its tax number, when known
/// @param country ISO 3166-1 alpha-2 code
public record WorkshopFiscalProfile(String name, @Nullable String nif, String country) {
}
