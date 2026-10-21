package com.asp.platform.customer.domain;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// Portuguese plates: six characters in three pairs of letters or digits, printed with hyphens.
/// Numbering is sequential, so each layout identifies a period of first registration.
public final class PortuguesePlate implements PlateFormat {

    private static final Map<PlateEra, Pattern> LAYOUTS = Map.of(
            PlateEra.PT_2020, Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{2}$"),
            PlateEra.PT_2005, Pattern.compile("^[0-9]{2}[A-Z]{2}[0-9]{2}$"),
            PlateEra.PT_1992, Pattern.compile("^[0-9]{4}[A-Z]{2}$"),
            PlateEra.PT_1937, Pattern.compile("^[A-Z]{2}[0-9]{4}$"));

    private static final int PAIR = 2;

    @Override
    public String country() {
        return "PT";
    }

    @Override
    public Optional<PlateEra> eraOf(String normalizedPlate) {
        return LAYOUTS.entrySet().stream()
                .filter(layout -> layout.getValue().matcher(normalizedPlate).matches())
                .map(Map.Entry::getKey)
                .findFirst();
    }

    @Override
    public String display(String normalizedPlate) {
        return normalizedPlate.substring(0, PAIR) + "-" + normalizedPlate.substring(PAIR, 2 * PAIR) + "-"
                + normalizedPlate.substring(2 * PAIR);
    }
}
