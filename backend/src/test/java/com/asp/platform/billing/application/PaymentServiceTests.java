package com.asp.platform.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.asp.platform.billing.domain.InvalidPhoneException;

class PaymentServiceTests {

    @ParameterizedTest
    @ValueSource(strings = {"912345678", "+351 912 345 678", "00351912345678", " 912-345-678 ", "+351912345678"})
    void should_normalise_a_portuguese_mobile_number(String phone) {
        assertThat(PaymentService.normalizePhone(phone)).isEqualTo("+351912345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"812345678", "91234567", "9123456789", "+34912345678", "abc", ""})
    void should_refuse_anything_else(String phone) {
        assertThatThrownBy(() -> PaymentService.normalizePhone(phone)).isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void should_refuse_a_missing_number() {
        assertThatThrownBy(() -> PaymentService.normalizePhone(null)).isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void should_mask_all_but_the_first_and_last_three_digits() {
        assertThat(PaymentService.mask("+351912345678")).isEqualTo("+351 9** *** 678");
    }
}
