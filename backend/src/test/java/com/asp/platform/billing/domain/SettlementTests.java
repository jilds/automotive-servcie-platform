package com.asp.platform.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SettlementTests {

    @Test
    void should_report_unpaid_partial_and_paid() {
        Invoice invoice = Fixtures.issued();

        Settlement none = Settlement.of(invoice, List.of(), false);
        assertThat(none.progress()).isEqualTo(PaymentProgress.UNPAID);
        assertThat(none.balanceCents()).isEqualTo(1000);

        Settlement partial = Settlement.of(invoice, List.of(Fixtures.payment(PaymentStatus.PAID, 400),
                Fixtures.payment(PaymentStatus.PENDING, 300)), false);
        assertThat(partial.progress()).isEqualTo(PaymentProgress.PARTIALLY_PAID);
        assertThat(partial.paidCents()).isEqualTo(400);
        assertThat(partial.pendingCents()).isEqualTo(300);
        assertThat(partial.balanceCents()).isEqualTo(600);

        Settlement paid = Settlement.of(invoice, List.of(Fixtures.payment(PaymentStatus.PAID, 1000)), false);
        assertThat(paid.progress()).isEqualTo(PaymentProgress.PAID);
        assertThat(paid.balanceCents()).isZero();
    }

    @Test
    void should_report_the_overpaid_amount_and_never_a_negative_balance() {
        Settlement settlement = Settlement.of(Fixtures.issued(), List.of(Fixtures.payment(PaymentStatus.PAID, 1000),
                Fixtures.payment(PaymentStatus.PAID, 500)), false);

        assertThat(settlement.progress()).isEqualTo(PaymentProgress.PAID);
        assertThat(settlement.overpaidCents()).isEqualTo(500);
        assertThat(settlement.balanceCents()).isZero();
    }

    @Test
    void should_owe_back_what_was_paid_on_a_credited_invoice() {
        Settlement settlement = Settlement.of(Fixtures.issued(), List.of(Fixtures.payment(PaymentStatus.PAID, 300),
                Fixtures.payment(PaymentStatus.PENDING, 200)), true);

        assertThat(settlement.progress()).isEqualTo(PaymentProgress.CREDITED);
        assertThat(settlement.refundDueCents()).isEqualTo(300);
        assertThat(settlement.balanceCents()).isZero();
        assertThat(settlement.pendingCents()).isZero();
    }

    @Test
    void should_owe_back_what_was_paid_on_a_voided_invoice_without_a_progress() {
        Invoice invoice = Fixtures.issued();
        invoice.voidIt("Engano", Fixtures.USER, Fixtures.NOW);

        Settlement settlement = Settlement.of(invoice, List.of(Fixtures.payment(PaymentStatus.PAID, 500)), false);

        assertThat(settlement.progress()).isNull();
        assertThat(settlement.refundDueCents()).isEqualTo(500);
        assertThat(settlement.balanceCents()).isZero();
    }

    @Test
    void should_have_no_progress_for_a_document_that_is_not_issued() {
        Settlement settlement = Settlement.of(Fixtures.pending(), List.of(), false);

        assertThat(settlement.progress()).isNull();
        assertThat(settlement.refundDueCents()).isZero();
    }

    @Test
    void should_leave_what_is_left_to_ask_for() {
        assertThat(Settlement.left(1000, List.of(Fixtures.payment(PaymentStatus.PAID, 400),
                Fixtures.payment(PaymentStatus.PENDING, 300), Fixtures.payment(PaymentStatus.CANCELLED, 300))))
                .isEqualTo(300);
        assertThat(Settlement.left(1000, List.of(Fixtures.payment(PaymentStatus.PAID, 1500)))).isZero();
    }
}
