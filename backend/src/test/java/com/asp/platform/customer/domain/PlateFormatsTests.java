package com.asp.platform.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PlateFormatsTests {

    private final PlateFormats plates = new PlateFormats(List.of(new PortuguesePlate()));

    @Test
    void should_refuse_a_portuguese_plate_that_fits_no_layout() {
        assertThatThrownBy(() -> plates.check("PT", "ABC123"))
                .isInstanceOf(InvalidPlateException.class)
                .hasMessage("Plate ABC123 is not a valid PT plate")
                .extracting("country").isEqualTo("PT");
        assertThatNoException().isThrownBy(() -> plates.check("PT", "12AB34"));
    }

    @Test
    void should_accept_any_plate_of_a_country_without_a_format() {
        assertThatNoException().isThrownBy(() -> plates.check("ES", "1234BCD"));
        assertThatNoException().isThrownBy(() -> plates.check("DE", "MAB1234"));
        assertThat(plates.format("DE", "MAB1234")).isEqualTo(new FormattedPlate("MAB1234", null));
    }

    @Test
    void should_format_portuguese_plates_and_show_old_unmatched_ones_as_stored() {
        assertThat(plates.format("PT", "AA00AA")).isEqualTo(new FormattedPlate("AA-00-AA", PlateEra.PT_2020));
        assertThat(plates.format("PT", "ABC123")).isEqualTo(new FormattedPlate("ABC123", null));
    }

    @Test
    void should_take_new_countries_without_changing_existing_formats() {
        PlateFormat spain = new PlateFormat() {
            @Override
            public String country() {
                return "ES";
            }

            @Override
            public Optional<PlateEra> eraOf(String plate) {
                return plate.matches("\\d{4}[A-Z]{3}") ? Optional.of(PlateEra.PT_2020) : Optional.empty();
            }

            @Override
            public String display(String plate) {
                return plate.substring(0, 4) + " " + plate.substring(4);
            }
        };
        PlateFormats both = new PlateFormats(List.of(new PortuguesePlate(), spain));

        assertThat(both.format("ES", "1234BCD").display()).isEqualTo("1234 BCD");
        assertThatThrownBy(() -> both.check("ES", "12AB34")).isInstanceOf(InvalidPlateException.class);
        assertThat(both.format("PT", "12AB34").display()).isEqualTo("12-AB-34");
    }

    @Test
    void should_refuse_two_formats_for_one_country() {
        assertThatThrownBy(() -> new PlateFormats(List.of(new PortuguesePlate(), new PortuguesePlate())))
                .isInstanceOf(IllegalStateException.class);
    }
}
