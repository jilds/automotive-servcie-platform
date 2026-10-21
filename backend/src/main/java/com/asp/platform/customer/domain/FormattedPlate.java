package com.asp.platform.customer.domain;

import org.jspecify.annotations.Nullable;

/// A stored plate as it is shown to people.
///
/// @param display the plate as printed on the vehicle, or the stored plate when its country has no known
///                format or the plate predates validation
/// @param era     the numbering series, or null when unknown
public record FormattedPlate(String display, @Nullable PlateEra era) {
}
