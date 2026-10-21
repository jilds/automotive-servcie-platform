package com.asp.platform.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class VehicleRegistrationTests {

    @Test
    void should_store_the_country_upper_case() {
        assertThat(new VehicleRegistration(" pt ", null).country()).isEqualTo("PT");
        assertThat(new VehicleRegistration("es", null).isIn("ES")).isTrue();
    }

    @Test
    void should_refuse_a_country_that_is_not_iso_alpha_2() {
        assertThatThrownBy(() -> new VehicleRegistration("XX", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VehicleRegistration("PRT", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_registration_months_from_1937() {
        assertThat(new VehicleRegistration("PT", YearMonth.of(1937, 1)).date()).isEqualTo(YearMonth.of(1937, 1));
        assertThatThrownBy(() -> new VehicleRegistration("PT", YearMonth.of(1936, 12)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
