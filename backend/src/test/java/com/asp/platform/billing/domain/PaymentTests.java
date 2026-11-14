package com.asp.platform.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentTests {

    /// The whole transition table of what a provider can say about a payment.
    @ParameterizedTest
    @CsvSource({
            "PENDING,PAID,true,PAID",
            "PENDING,FAILED,true,FAILED",
            "PENDING,EXPIRED,true,EXPIRED",
            "PENDING,PENDING,false,PENDING",
            "EXPIRED,PAID,true,PAID",
            "CANCELLED,PAID,true,PAID",
            "EXPIRED,FAILED,false,EXPIRED",
            "EXPIRED,EXPIRED,false,EXPIRED",
            "CANCELLED,FAILED,false,CANCELLED",
            "CANCELLED,EXPIRED,false,CANCELLED",
            "PAID,PAID,false,PAID",
            "PAID,FAILED,false,PAID",
            "PAID,EXPIRED,false,PAID",
            "FAILED,PAID,false,FAILED",
            "FAILED,FAILED,false,FAILED",
            "FAILED,EXPIRED,false,FAILED"})
    void should_follow_the_transition_table(PaymentStatus from, ProviderPaymentState state, boolean changed,
            PaymentStatus to) {
        Payment payment = Fixtures.payment(from, 500);

        assertThat(payment.applyProviderState(state, Fixtures.NOW.plusSeconds(60))).isEqualTo(changed);
        assertThat(payment.status()).isEqualTo(to);
        if (to == PaymentStatus.PAID && changed) {
            assertThat(payment.paidAt()).isEqualTo(Fixtures.NOW.plusSeconds(60));
        }
    }

    @Test
    void should_cancel_only_a_pending_payment() {
        Payment pending = Fixtures.payment(PaymentStatus.PENDING, 500);
        pending.cancel();
        assertThat(pending.status()).isEqualTo(PaymentStatus.CANCELLED);

        for (PaymentStatus other : new PaymentStatus[] {PaymentStatus.PAID, PaymentStatus.FAILED,
                PaymentStatus.EXPIRED, PaymentStatus.CANCELLED}) {
            assertThatThrownBy(Fixtures.payment(other, 500)::cancel).isInstanceOf(PaymentNotPendingException.class);
        }
    }

    @Test
    void should_record_an_instalment_sale_as_paid_and_keep_its_data() {
        Payment p = Payment.instalments(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), 9000,
                new Instalment(Financier.PARCELA_JA, 6, "R-1"), null, Fixtures.USER, Fixtures.NOW);

        assertThat(p.method()).isEqualTo(PaymentMethod.INSTALMENTS);
        assertThat(p.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.instalment()).isEqualTo(new Instalment(Financier.PARCELA_JA, 6, "R-1"));
        assertThatThrownBy(() -> Payment.recorded(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                PaymentMethod.INSTALMENTS, 500, null, Fixtures.USER, Fixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_two_to_six_instalments_only() {
        assertThat(new Instalment(Financier.OTHER, 2, null).count()).isEqualTo(2);
        assertThatThrownBy(() -> new Instalment(Financier.OTHER, 1, null))
                .isInstanceOf(InvalidInstalmentException.class);
        assertThatThrownBy(() -> new Instalment(Financier.OTHER, 7, null))
                .isInstanceOf(InvalidInstalmentException.class);
        assertThatThrownBy(() -> new Instalment(Financier.OTHER, 3, " "))
                .isInstanceOf(InvalidInstalmentException.class);
    }

    @Test
    void should_record_staff_methods_as_paid_at_once_and_refuse_provider_methods_there() {
        Payment cash = Payment.recorded(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), PaymentMethod.CASH,
                500, null, Fixtures.USER, Fixtures.NOW);

        assertThat(cash.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(cash.paidAt()).isEqualTo(Fixtures.NOW);
        assertThatThrownBy(() -> Payment.recorded(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                PaymentMethod.MBWAY, 500, null, Fixtures.USER, Fixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
