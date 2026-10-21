package com.asp.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class WorkshopSettingsTests {

    @Test
    void should_start_new_workshops_with_portuguese_defaults() {
        WorkshopSettings defaults = WorkshopSettings.portugueseDefaults(" Oficina ", " ");

        assertThat(defaults.name()).isEqualTo("Oficina");
        assertThat(defaults.nif()).isNull();
        assertThat(defaults.country()).isEqualTo("PT");
        assertThat(defaults.zone()).isEqualTo(ZoneId.of("Europe/Lisbon"));
        assertThat(defaults.defaultVatRate()).isEqualTo(new BigDecimal("23.00"));
        assertThat(defaults.labourRateCents()).isZero();
    }

    @Test
    void should_normalize_country_and_vat_scale() {
        WorkshopSettings settings = settings("es", "Europe/Madrid", "21", 4500);

        assertThat(settings.country()).isEqualTo("ES");
        assertThat(settings.defaultVatRate()).isEqualTo(new BigDecimal("21.00"));
    }

    @Test
    void should_reject_an_unknown_country() {
        assertThatThrownBy(() -> settings("XX", "Europe/Lisbon", "23", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_an_unknown_time_zone() {
        assertThatThrownBy(() -> settings("PT", "Europe/Atlantis", "23", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_vat_outside_0_to_100_or_with_more_than_two_decimals() {
        assertThatThrownBy(() -> settings("PT", "Europe/Lisbon", "100.01", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings("PT", "Europe/Lisbon", "-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings("PT", "Europe/Lisbon", "6.125", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(settings("PT", "Europe/Lisbon", "6.50", 0).defaultVatRate()).isEqualTo(new BigDecimal("6.50"));
    }

    @Test
    void should_reject_a_negative_labour_rate() {
        assertThatThrownBy(() -> settings("PT", "Europe/Lisbon", "23", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WorkshopSettings settings(String country, String zone, String vat, long labour) {
        return new WorkshopSettings("Oficina", "123456789", country, zone, new BigDecimal(vat), labour);
    }
}
