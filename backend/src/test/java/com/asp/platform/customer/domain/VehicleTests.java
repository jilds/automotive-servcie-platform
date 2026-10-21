package com.asp.platform.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VehicleTests {

    private static final PlateFormats PLATES = new PlateFormats(List.of(new PortuguesePlate()));
    private static final VehicleRegistration PT = new VehicleRegistration("PT", null);
    private static final VehicleRegistration ES = new VehicleRegistration("ES", null);

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
        Vehicle vehicle = vehicleAt(100_000);

        vehicle.recordMileage(90_000);
        assertThat(vehicle.mileage()).isEqualTo(100_000);

        vehicle.recordMileage(110_000);
        assertThat(vehicle.mileage()).isEqualTo(110_000);
    }

    @Test
    void should_edit_details_and_raise_the_mileage() {
        Vehicle vehicle = vehicleAt(100_000);

        vehicle.edit(new VehicleDetails("bb-11-cc", null, "Peugeot", "208", 2021, "Diesel"), PT, 100_500, PLATES);

        assertThat(vehicle.details().plate()).isEqualTo("BB11CC");
        assertThat(vehicle.details().make()).isEqualTo("Peugeot");
        assertThat(vehicle.mileage()).isEqualTo(100_500);
    }

    @Test
    void should_keep_the_mileage_when_an_edit_leaves_it_out() {
        Vehicle vehicle = vehicleAt(100_000);

        vehicle.edit(details("AA00AA"), PT, null, PLATES);

        assertThat(vehicle.mileage()).isEqualTo(100_000);
    }

    @Test
    void should_refuse_an_edit_that_lowers_the_mileage() {
        Vehicle vehicle = vehicleAt(100_000);

        assertThatThrownBy(() -> vehicle.edit(details("ZZ99ZZ"), PT, 99_999, PLATES))
                .isInstanceOf(MileageDecreaseException.class)
                .extracting("code").isEqualTo("mileage-decrease");
        assertThat(vehicle.details().plate()).isEqualTo("AA00AA");
        assertThat(vehicle.mileage()).isEqualTo(100_000);
    }

    @Test
    void should_accept_any_mileage_when_none_was_recorded() {
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), UUID.randomUUID(), details("AA00AA"), PT, null);

        vehicle.edit(vehicle.details(), PT, 5, PLATES);

        assertThat(vehicle.mileage()).isEqualTo(5);
    }

    @Test
    void should_transfer_to_another_customer() {
        Vehicle vehicle = vehicleAt(100_000);
        UUID buyer = UUID.randomUUID();

        vehicle.transferTo(buyer);

        assertThat(vehicle.customerId()).isEqualTo(buyer);
    }

    @Test
    void should_register_a_vehicle_only_with_a_plate_that_fits_its_country() {
        assertThat(Vehicle.register(UUID.randomUUID(), UUID.randomUUID(), details("12-ab-34"), PT, null, PLATES)
                .formattedPlate(PLATES)).isEqualTo(new FormattedPlate("12-AB-34", PlateEra.PT_2005));

        assertThatThrownBy(() -> Vehicle.register(UUID.randomUUID(), UUID.randomUUID(), details("ABC123"), PT, null,
                PLATES))
                .isInstanceOf(InvalidPlateException.class)
                .hasMessageContaining("ABC123");

        Vehicle spanish = Vehicle.register(UUID.randomUUID(), UUID.randomUUID(), details("1234 BCD"), ES, null,
                PLATES);
        assertThat(spanish.formattedPlate(PLATES)).isEqualTo(new FormattedPlate("1234BCD", null));
    }

    @Test
    void should_check_the_plate_when_it_changes_on_edit() {
        Vehicle vehicle = vehicleAt(100_000);

        assertThatThrownBy(() -> vehicle.edit(details("1234567"), PT, null, PLATES))
                .isInstanceOf(InvalidPlateException.class);
        assertThat(vehicle.details().plate()).isEqualTo("AA00AA");
    }

    @Test
    void should_check_the_plate_when_the_country_changes_to_one_with_a_format() {
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), UUID.randomUUID(), details("1234BCD"), ES, null);

        assertThatThrownBy(() -> vehicle.edit(details("1234BCD"), PT, null, PLATES))
                .isInstanceOf(InvalidPlateException.class);
        assertThat(vehicle.registration()).isEqualTo(ES);
    }

    @Test
    void should_keep_an_unchanged_plate_that_predates_the_format_check() {
        Vehicle legacy = new Vehicle(UUID.randomUUID(), UUID.randomUUID(), details("ABC123"), PT, 1_000);

        assertThat(legacy.formattedPlate(PLATES)).isEqualTo(new FormattedPlate("ABC123", null));

        YearMonth april2012 = YearMonth.of(2012, 4);
        legacy.edit(new VehicleDetails("abc 123", null, "Fiat", "Punto", null, null),
                new VehicleRegistration("pt", april2012), 2_000, PLATES);

        assertThat(legacy.details().make()).isEqualTo("Fiat");
        assertThat(legacy.registration().date()).isEqualTo(april2012);
    }

    private static Vehicle vehicleAt(int mileage) {
        return new Vehicle(UUID.randomUUID(), UUID.randomUUID(), details("AA00AA"), PT, mileage);
    }

    private static VehicleDetails details(String plate) {
        return new VehicleDetails(plate, null, null, null, null, null);
    }
}
