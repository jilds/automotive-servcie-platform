package com.asp.platform.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.asp.platform.billing.domain.PaymentMethod;

class CreatePaymentRequestTests {

    @Test
    void should_not_print_the_phone_number() {
        var request = new PaymentController.CreatePaymentRequest(PaymentMethod.MBWAY, 100L, "912345678", "nota");

        assertThat(request.toString()).doesNotContain("912345678").contains("MBWAY");
    }
}
