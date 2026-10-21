package com.asp.platform.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PortuguesePlateTests {

    private final PortuguesePlate format = new PortuguesePlate();

    @Test
    void should_be_the_format_of_portugal() {
        assertThat(format.country()).isEqualTo("PT");
    }

    @ParameterizedTest(name = "{0} is {1}, shown as {2}")
    @CsvSource({
            "AA00AA, PT_2020, AA-00-AA",
            "BX27QP, PT_2020, BX-27-QP",
            "00AA00, PT_2005, 00-AA-00",
            "12AB34, PT_2005, 12-AB-34",
            "0000AA, PT_1992, 00-00-AA",
            "9876XZ, PT_1992, 98-76-XZ",
            "AA0000, PT_1937, AA-00-00",
            "LX1234, PT_1937, LX-12-34"})
    void should_recognise_each_layout_and_print_it_with_hyphens(String plate, PlateEra era, String display) {
        assertThat(format.eraOf(plate)).contains(era);
        assertThat(format.display(plate)).isEqualTo(display);
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
            "aa-00-aa,  PT_2020, AA-00-AA",
            "12 ab 34,  PT_2005, 12-AB-34",
            "12-34-ab,  PT_1992, 12-34-AB",
            "' lx 12-34', PT_1937, LX-12-34"})
    void should_recognise_lower_case_spaced_and_hyphenated_input_once_normalized(String input, PlateEra era,
            String display) {
        String plate = VehicleDetails.normalizePlate(input);

        assertThat(format.eraOf(plate)).contains(era);
        assertThat(format.display(plate)).isEqualTo(display);
    }

    @ParameterizedTest(name = "{0} is not Portuguese")
    @ValueSource(strings = {
            "ABC123",   // three letters
            "123456",   // digits only
            "ABCDEF",   // letters only
            "A1B2C3",   // mixed inside a pair
            "AB12C",    // too short
            "AB12CD3",  // too long
            "12AB3C",   // last pair mixed
            "1234BCD",  // Spanish
            "AB-12-CD-", // normalized elsewhere, but a raw hyphen never matches
            "ÀB12CD"    // letters outside A-Z
    })
    void should_reject_plates_that_fit_no_layout(String plate) {
        assertThat(format.eraOf(plate)).isEmpty();
    }
}
