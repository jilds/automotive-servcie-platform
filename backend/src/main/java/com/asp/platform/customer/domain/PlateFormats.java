package com.asp.platform.customer.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// The plate formats known per registration country. Countries without a format accept any plate.
public final class PlateFormats {

    private final Map<String, PlateFormat> byCountry;

    public PlateFormats(Collection<? extends PlateFormat> formats) {
        this.byCountry = formats.stream().collect(Collectors.toUnmodifiableMap(PlateFormat::country,
                Function.identity(), (first, second) -> {
                    throw new IllegalStateException("Two plate formats for country " + first.country());
                }));
    }

    /// Refuses a plate that does not fit its country's format.
    ///
    /// @throws InvalidPlateException when the country has a format and the plate matches none of its layouts
    public void check(String country, String normalizedPlate) {
        Optional<PlateFormat> format = formatOf(country);
        if (format.isPresent() && format.get().eraOf(normalizedPlate).isEmpty()) {
            throw new InvalidPlateException(country, normalizedPlate);
        }
    }

    /// How a stored plate is shown. Never fails: plates saved before validation existed are shown as stored.
    public FormattedPlate format(String country, String normalizedPlate) {
        return formatOf(country)
                .flatMap(format -> format.eraOf(normalizedPlate)
                        .map(era -> new FormattedPlate(format.display(normalizedPlate), era)))
                .orElseGet(() -> new FormattedPlate(normalizedPlate, null));
    }

    private Optional<PlateFormat> formatOf(String country) {
        return Optional.ofNullable(byCountry.get(country));
    }
}
