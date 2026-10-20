package com.asp.platform.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VehicleTests {

    @Test
    void should_normalize_plates_so_spacing_and_case_do_not_matter() {
        assertThat(VehicleDetails.normalizePlate("aa-00 aa")).isEqualTo("AA00AA");
        assertThat(new VehicleDetails("12-ab-34", " vf1abc ", null, null, null, null).vin()).isEqualTo("VF1ABC");
    }

    @Test
    void should_reject_an_empty_plate() {
        assertThatThrownBy(() -> VehicleDetails.normalizePlate(" - ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_only_record_higher_odometer_readings() {
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), UUID.randomUUID(),
                new VehicleDetails("AA00AA", null, null, null, null, null), 100_000);

        vehicle.recordMileage(90_000);
        assertThat(vehicle.mileage()).isEqualTo(100_000);

        vehicle.recordMileage(110_000);
        assertThat(vehicle.mileage()).isEqualTo(110_000);
    }
}
