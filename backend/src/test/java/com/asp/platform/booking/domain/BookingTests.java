package com.asp.platform.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.asp.platform.shared.StaleDataException;

/// Status moves and the customer's cancel deadline.
class BookingTests {

    private static final Instant SLOT = Instant.parse("2026-11-02T09:00:00Z");
    private static final Instant BEFORE = SLOT.minus(Duration.ofDays(3));
    private static final UUID DESK = UUID.randomUUID();

    @Test
    void should_confirm_decline_and_move_only_from_the_allowed_statuses() {
        Booking b = requested();
        b.confirm(DESK, BEFORE);
        assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.decidedBy()).isEqualTo(DESK);
        assertThatThrownBy(() -> b.confirm(DESK, BEFORE)).isInstanceOf(IllegalBookingStatusException.class);
        assertThatThrownBy(() -> b.decline("x", DESK, BEFORE)).isInstanceOf(IllegalBookingStatusException.class);

        Instant later = SLOT.plus(Duration.ofHours(1));
        b.moveTo(later, DESK, BEFORE);
        assertThat(b.slotStart()).isEqualTo(later);
        assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED);

        Booking moved = requested();
        moved.moveTo(later, DESK, BEFORE);
        assertThat(moved.status()).isEqualTo(BookingStatus.CONFIRMED);

        Booking declined = requested();
        assertThatThrownBy(() -> declined.decline("  ", DESK, BEFORE)).isInstanceOf(DeclineReasonRequiredException.class);
        declined.decline(" Sem vagas ", DESK, BEFORE);
        assertThat(declined.status()).isEqualTo(BookingStatus.DECLINED);
        assertThat(declined.declineReason()).isEqualTo("Sem vagas");
        assertThatThrownBy(() -> declined.moveTo(later, DESK, BEFORE)).isInstanceOf(IllegalBookingStatusException.class);
    }

    @Test
    void should_mark_a_no_show_only_once_the_confirmed_slot_has_started() {
        Booking b = requested();
        assertThatThrownBy(() -> b.noShow(DESK, SLOT.plusSeconds(60))).isInstanceOf(IllegalBookingStatusException.class);
        b.confirm(DESK, BEFORE);
        assertThatThrownBy(() -> b.noShow(DESK, SLOT)).isInstanceOf(IllegalBookingStatusException.class);
        b.noShow(DESK, SLOT.plusSeconds(60));
        assertThat(b.status()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void should_let_the_customer_cancel_until_the_deadline() {
        Booking b = requested();
        Instant deadline = SLOT.minus(Duration.ofHours(24));
        assertThat(b.canCancel(deadline, 24)).isTrue();
        assertThat(b.canCancel(deadline.plusSeconds(1), 24)).isFalse();
        assertThatThrownBy(() -> b.cancelByCustomer(deadline.plusSeconds(1), 24))
                .isInstanceOf(BookingCancelTooLateException.class);

        b.cancelByCustomer(deadline, 24);
        assertThat(b.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.canCancel(BEFORE, 24)).isFalse();
        assertThatThrownBy(() -> b.cancelByCustomer(BEFORE, 24)).isInstanceOf(IllegalBookingStatusException.class);
    }

    @Test
    void should_check_in_only_a_confirmed_booking() {
        Booking b = requested();
        UUID vehicle = UUID.randomUUID();
        assertThatThrownBy(() -> b.claimFor(vehicle)).isInstanceOf(BookingNotConfirmedException.class);
        b.confirm(DESK, BEFORE);
        b.claimFor(vehicle);
        UUID order = UUID.randomUUID();
        b.checkedIn(order);
        assertThat(b.status()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(b.vehicleId()).isEqualTo(vehicle);
        assertThat(b.workOrderId()).isEqualTo(order);
        assertThatThrownBy(() -> b.checkedIn(order)).isInstanceOf(BookingNotConfirmedException.class);
        assertThatThrownBy(() -> b.requireVersion(1)).isInstanceOf(StaleDataException.class);
    }

    @Test
    void should_need_a_phone_or_an_email_and_price_services_with_vat_half_up() {
        assertThatThrownBy(() -> new BookingContact("Ana", " ", null, "AA00AA", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new BookingContact(" Ana ", null, "ana@example.pt", "AA00AA", " ").note()).isNull();
        // 22.50 + 23 % = 27.675, so 27.68.
        assertThat(new BookedService(UUID.randomUUID(), "Óleo", 60, 2250, BigDecimal.valueOf(23))
                .priceWithVatCents()).isEqualTo(2768);
    }

    private static Booking requested() {
        return Booking.request(UUID.randomUUID(), 1, SLOT, new BookingContact("Ana", "912345678", null, "AA00AA",
                null), List.of(new BookedService(UUID.randomUUID(), "Óleo", 60, 4000, BigDecimal.valueOf(23))), false,
                "0".repeat(64), BEFORE);
    }
}
