package com.asp.platform.workshop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// The quote's rules without a database: snapshot and totals, VAT by rate, the effective status, decisions and the
/// closing moves.
class QuoteTests {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private static WorkOrder order(WorkOrderStatus status, NewLine... lines) {
        WorkOrder order = new WorkOrder(UUID.randomUUID(), 1, UUID.randomUUID(), new CheckInDetails(null, null, null,
                null), WorkOrderStatus.DIAGNOSTICS, null, NOW, List.of());
        for (NewLine line : lines) {
            order.addLine(UUID.randomUUID(), line);
        }
        if (status != WorkOrderStatus.DIAGNOSTICS) {
            order.moveTo(status, NOW);
        }
        return order;
    }

    private static NewLine labour(long cents, String vat) {
        return new NewLine(LineType.LABOR, "Mão de obra", BigDecimal.ONE, cents, null, new BigDecimal(vat));
    }

    private static Quote send(WorkOrder order) {
        return Quote.send(UUID.randomUUID(), 1, order, UUID.randomUUID(), "h".repeat(64), NOW, 15, UUID::randomUUID);
    }

    @Test
    void should_copy_the_lines_and_totals_and_group_vat_by_rate() {
        WorkOrder order = order(WorkOrderStatus.DIAGNOSTICS, labour(4000, "23"), labour(1000, "6"),
                labour(333, "23.00"));

        Quote quote = send(order);

        assertThat(quote.lines()).extracting(QuoteLine::position).containsExactly(1, 2, 3);
        assertThat(quote.netCents()).isEqualTo(5333);
        assertThat(quote.vatCents()).isEqualTo(order.vatCents());
        assertThat(quote.totalCents()).isEqualTo(order.totalCents());
        assertThat(quote.validUntil()).isEqualTo(NOW.plus(Duration.ofDays(15)));
        assertThat(quote.vatByRate()).containsExactly(
                new VatTotal(new BigDecimal("6.00"), 1000, 60),
                new VatTotal(new BigDecimal("23.00"), 4333, 920 + 77));
    }

    @Test
    void should_refuse_orders_that_are_not_quotable() {
        assertThatThrownBy(() -> send(order(WorkOrderStatus.DIAGNOSTICS)))
                .isInstanceOf(WorkOrderNotQuotableException.class);
        assertThatThrownBy(() -> send(order(WorkOrderStatus.IN_PROGRESS, labour(100, "23"))))
                .isInstanceOf(WorkOrderNotQuotableException.class);
        assertThat(send(order(WorkOrderStatus.AWAITING_APPROVAL, labour(100, "23"))).status())
                .isEqualTo(QuoteStatus.SENT);
    }

    @Test
    void should_read_expired_after_its_validity_and_refuse_decisions() {
        Quote quote = send(order(WorkOrderStatus.DIAGNOSTICS, labour(100, "23")));
        Instant later = NOW.plus(Duration.ofDays(16));

        assertThat(quote.statusAt(NOW)).isEqualTo(QuoteStatus.SENT);
        assertThat(quote.statusAt(later)).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(quote.canDecide(later)).isFalse();
        assertThatThrownBy(() -> quote.approveByCustomer("Ana", later)).isInstanceOf(QuoteExpiredException.class);

        quote.supersede(later);
        assertThat(quote.status()).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void should_decide_once_and_need_a_note_from_the_staff() {
        Quote quote = send(order(WorkOrderStatus.DIAGNOSTICS, labour(100, "23")));
        UUID desk = UUID.randomUUID();

        assertThatThrownBy(() -> quote.decideByStaff(QuoteDecision.APPROVED, " ", desk, NOW))
                .isInstanceOf(DecisionNoteRequiredException.class);
        quote.decideByStaff(QuoteDecision.REJECTED, " Caro ", desk, NOW);

        assertThat(quote.status()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(quote.decidedChannel()).isEqualTo(QuoteChannel.STAFF);
        assertThat(quote.decidedBy()).isEqualTo(desk);
        assertThat(quote.decidedNote()).isEqualTo("Caro");
        assertThatThrownBy(() -> quote.approveByCustomer("Ana", NOW)).isInstanceOf(QuoteNotOpenException.class);
        assertThatThrownBy(() -> quote.supersede(NOW)).isInstanceOf(QuoteNotOpenException.class);
    }

    @Test
    void should_know_when_a_part_was_typed_by_hand() {
        NewLine typed = new NewLine(LineType.PART, "Pastilhas", BigDecimal.ONE, 1000, null, new BigDecimal("23"));
        NewLine fromStock = new NewLine(LineType.PART, "Filtro", BigDecimal.ONE, 1000, null, new BigDecimal("23"),
                null, UUID.randomUUID());

        assertThat(send(order(WorkOrderStatus.DIAGNOSTICS, typed)).hasPartsToOrder()).isTrue();
        assertThat(send(order(WorkOrderStatus.DIAGNOSTICS, fromStock, labour(100, "23"))).hasPartsToOrder()).isFalse();
    }
}
