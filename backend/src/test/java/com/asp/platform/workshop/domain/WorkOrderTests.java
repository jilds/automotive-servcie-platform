package com.asp.platform.workshop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkOrderTests {

    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");
    private static final CheckInDetails CHECK_IN = new CheckInDetails(120_000, 4, "Brake noise", null);

    private final WorkOrder order = WorkOrder.open(UUID.randomUUID(), 1, UUID.randomUUID(), CHECK_IN, null, NOW);

    @Test
    void should_open_in_diagnostics() {
        assertThat(order.status()).isEqualTo(WorkOrderStatus.DIAGNOSTICS);
        assertThat(order.statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    void should_total_lines_with_discount_and_vat_rounded_half_up() {
        order.addLine(UUID.randomUUID(), line(LineType.LABOR, "1.5", 4000, null));
        order.addLine(UUID.randomUUID(), line(LineType.PART, "2", 1250, "10"));

        assertThat(order.netCents()).isEqualTo(8250);
        assertThat(order.vatCents()).isEqualTo(1898);
        assertThat(order.totalCents()).isEqualTo(10148);
        assertThat(order.lines()).extracting(WorkOrderLine::position).containsExactly(1, 2);
    }

    @Test
    void should_reject_a_move_the_workflow_does_not_allow() {
        assertThatThrownBy(() -> order.moveTo(WorkOrderStatus.DELIVERED, NOW))
                .isInstanceOf(IllegalStatusChangeException.class);
    }

    @Test
    void should_lock_lines_once_the_car_is_ready() {
        order.moveTo(WorkOrderStatus.IN_PROGRESS, NOW);
        order.moveTo(WorkOrderStatus.READY, NOW.plusSeconds(60));

        assertThat(order.statusChangedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> order.addLine(UUID.randomUUID(), line(LineType.PART, "1", 100, null)))
                .isInstanceOf(WorkOrderLockedException.class);
    }

    @Test
    void should_fail_when_removing_an_unknown_line() {
        assertThatThrownBy(() -> order.removeLine(UUID.randomUUID())).isInstanceOf(LineNotFoundException.class);
    }

    @Test
    void should_keep_delivered_and_cancelled_off_the_board() {
        assertThat(WorkOrderStatus.boardColumns())
                .doesNotContain(WorkOrderStatus.DELIVERED, WorkOrderStatus.CANCELLED)
                .hasSize(5);
    }

    @Test
    void should_reject_a_fuel_level_above_full() {
        assertThatThrownBy(() -> new CheckInDetails(null, 9, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static NewLine line(LineType type, String quantity, long unitPriceCents, String discount) {
        return new NewLine(type, "line", new BigDecimal(quantity), unitPriceCents,
                discount == null ? null : new BigDecimal(discount), BigDecimal.valueOf(23));
    }
}
