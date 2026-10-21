package com.asp.platform.customer.domain;

import java.util.Optional;

/// The plate rules of one registration country. Add a country by adding an implementation and
/// registering it in [PlateFormats]; existing formats stay untouched.
public interface PlateFormat {

    /// ISO 3166-1 alpha-2 code of the country whose plates this format describes, for example "PT".
    String country();

    /// The series a plate belongs to, or empty when it matches none of the country's layouts.
    ///
    /// @param normalizedPlate upper case, without spaces or dashes (see [VehicleDetails#normalizePlate])
    Optional<PlateEra> eraOf(String normalizedPlate);

    /// The plate as printed on the vehicle, for example "12-AB-34". Only called for plates that match.
    String display(String normalizedPlate);
}
