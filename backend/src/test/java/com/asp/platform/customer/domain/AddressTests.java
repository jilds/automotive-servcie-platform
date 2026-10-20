package com.asp.platform.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AddressTests {

    @Test
    void should_trim_fields_and_default_to_portugal() {
        Address address = new Address(" Rua Augusta, 120 ", " ", "1100-053", " Lisboa ", "");

        assertThat(address.street()).isEqualTo("Rua Augusta, 120");
        assertThat(address.detail()).isNull();
        assertThat(address.locality()).isEqualTo("Lisboa");
        assertThat(address.country()).isEqualTo("PT");
    }

    @Test
    void should_reject_a_portuguese_postal_code_in_the_wrong_format() {
        assertThatThrownBy(() -> new Address("Rua A", null, "1100053", "Lisboa", "PT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_foreign_postal_codes_as_written() {
        assertThat(new Address("Calle Mayor 1", null, "28013", "Madrid", "es").country()).isEqualTo("ES");
    }

    @Test
    void should_require_street_postal_code_and_locality() {
        assertThatThrownBy(() -> new Address("Rua A", null, "1100-053", " ", "PT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
